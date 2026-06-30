package auth

import (
	"crypto/rand"
	"encoding/hex"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/crypto/bcrypt"
)

var secretKey = []byte(getSecret())

func getSecret() string {
	if s := os.Getenv("JWT_SECRET"); s != "" {
		return s
	}
	return "change-this-to-a-long-random-string"
}

// HashPassword uses bcrypt at a low cost to stay light on CPU.
// cost=10 is the default; we use 10 (fine for a personal server).
func HashPassword(password string) (string, error) {
	b, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	return string(b), err
}

func VerifyPassword(plain, hashed string) bool {
	return bcrypt.CompareHashAndPassword([]byte(hashed), []byte(plain)) == nil
}

type Claims struct {
	UserID int64 `json:"uid"`
	jwt.RegisteredClaims
}

const (
	AccessTokenTTL  = 15 * time.Minute
	RefreshTokenTTL = 30 * 24 * time.Hour // 30 days
)

func CreateToken(userID int64) (string, error) {
	claims := Claims{
		UserID: userID,
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(AccessTokenTTL)),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
		},
	}
	t := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return t.SignedString(secretKey)
}

// NewRefreshToken returns a cryptographically random 256-bit hex token.
func NewRefreshToken() (string, error) {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

// NewShareToken returns a 128-bit hex token suitable for share links.
func NewShareToken() (string, error) {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

func ParseToken(tokenStr string) (*Claims, error) {
	t, err := jwt.ParseWithClaims(tokenStr, &Claims{}, func(t *jwt.Token) (interface{}, error) {
		return secretKey, nil
	})
	if err != nil || !t.Valid {
		return nil, err
	}
	return t.Claims.(*Claims), nil
}

const UserIDKey = "userID"

// Middleware extracts and validates the Bearer token, storing the userID in the context.
func Middleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		header := c.GetHeader("Authorization")
		if !strings.HasPrefix(header, "Bearer ") {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "missing token"})
			return
		}
		claims, err := ParseToken(strings.TrimPrefix(header, "Bearer "))
		if err != nil || claims == nil {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "invalid token"})
			return
		}
		c.Set(UserIDKey, claims.UserID)
		c.Next()
	}
}

// CurrentUserID pulls the authenticated user's ID from the Gin context.
func CurrentUserID(c *gin.Context) int64 {
	if v, exists := c.Get(UserIDKey); exists {
		if id, ok := v.(int64); ok {
			return id
		}
	}
	// should never reach here if Middleware() is applied
	panic("auth.CurrentUserID called outside authenticated route")
}

// ParseID parses a string path parameter to int64.
func ParseID(s string) (int64, error) {
	return strconv.ParseInt(s, 10, 64)
}
