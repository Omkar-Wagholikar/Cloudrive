package main

import (
	"log"
	"os"
	"os/signal"
	"syscall"

	"cloudrive/internal/auth"
	"cloudrive/internal/database"
	"cloudrive/internal/files"
	"cloudrive/internal/network"
	"cloudrive/internal/queue"
	"cloudrive/internal/thumbnail"

	"github.com/gin-gonic/gin"
)

func main() {
	// Initialise DB
	db, err := database.Init("data/cloud.db")
	if err != nil {
		log.Fatalf("db init: %v", err)
	}

	// SeaweedFS master address (override via env)
	seaweedMaster := getEnv("SEAWEEDFS_MASTER", "localhost:9333")

	// Kafka connection (Aiven mTLS)
	// Cert files can be placed anywhere; set these env vars to their paths.
	kafkaBroker := getEnv("KAFKA_BROKER", "kafka-omkargwagholikar.e.aivencloud.com:12128")
	q, err := queue.NewKafkaQueue(
		[]string{kafkaBroker},
		queue.KafkaCerts{
			AccessKeyFile:  getEnv("KAFKA_ACCESS_KEY",  "certs/service.key"),
			AccessCertFile: getEnv("KAFKA_ACCESS_CERT", "certs/service.cert"),
			CACertFile:     getEnv("KAFKA_CA_CERT",     "certs/ca.pem"),
		},
	)
	if err != nil {
		log.Fatalf("queue init: %v", err)
	}

	defer q.Close()

	// Init thumbnail worker (runs background goroutine)
	thumbWorker := thumbnail.NewWorker(db, q, seaweedMaster)
	thumbWorker.Start()

	// Graceful shutdown on SIGINT / SIGTERM
	// Must be after thumbWorker is declared so it's in scope.
	go func() {
		sig := make(chan os.Signal, 1)
		signal.Notify(sig, syscall.SIGINT, syscall.SIGTERM)
		<-sig
		log.Println("shutting down…")
		thumbWorker.Stop() // signal worker to exit before closing Kafka client
		q.Close()
		os.Exit(0)
	}()

	// Build router
	r := gin.Default()

	addr := getEnv("LISTEN_ADDR", ":8081")

	localAddrs, err := network.LocalAddresses(addr)
	if err != nil {
		log.Printf("WARNING: could not enumerate local addresses: %v", err)
	}

	authMiddleware := auth.Middleware()

	// Public routes
	r.POST("/register", files.RegisterHandler(db))
	r.POST("/login", files.LoginHandler(db))
	r.POST("/token/refresh", files.RefreshHandler(db))
	r.GET("/network", files.NetworkHandler(localAddrs))
	r.GET("/shared/:token", files.SharedDownloadHandler(db, seaweedMaster))

	protected := r.Group("/")
	protected.Use(authMiddleware)
	{
		// Auth
		protected.POST("/logout", files.LogoutHandler(db))
		protected.GET("/me", files.MeHandler(db))

		// Files
		protected.POST("/upload", files.UploadHandler(db, q, seaweedMaster, localAddrs))
		protected.GET("/files", files.ListFilesHandler(db))
		protected.GET("/files/search", files.SearchFilesHandler(db))
		protected.GET("/files/:id", files.FileInfoHandler(db, localAddrs))
		protected.PATCH("/files/:id", files.PatchFileHandler(db))
		protected.DELETE("/files/:id", files.DeleteFileHandler(db))
		protected.GET("/files/:id/download", files.DownloadHandler(db, seaweedMaster))
		protected.GET("/files/:id/thumbnail", files.ThumbnailHandler(db, seaweedMaster))
		protected.POST("/files/:id/share", files.CreateShareHandler(db))

		// Thumbnails listing
		protected.GET("/thumbnails", files.ListThumbnailsHandler(db))

		// Shared link management
		protected.DELETE("/shared/:token", files.RevokeShareHandler(db))

		// Trash
		protected.GET("/trash", files.ListTrashHandler(db))
		protected.POST("/trash/:id/restore", files.RestoreFileHandler(db))
		protected.DELETE("/trash/:id", files.PurgeFileHandler(db, seaweedMaster))

		// Resumable uploads
		protected.POST("/uploads/resumable", files.InitResumableHandler(db))
		protected.GET("/uploads/resumable/:upload_id", files.UploadStatusHandler(db))
		protected.PATCH("/uploads/resumable/:upload_id", files.ResumeUploadHandler(db, q, seaweedMaster, localAddrs))

		// Folders
		protected.POST("/folders", files.CreateFolderHandler(db))
		protected.GET("/folders", files.ListFoldersHandler(db))
		protected.GET("/folders/:id", files.GetFolderHandler(db))
		protected.DELETE("/folders/:id", files.DeleteFolderHandler(db))
	}

	log.Printf("Listening on %s", addr)
	if err := r.Run(addr); err != nil {
		log.Fatalf("server: %v", err)
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
