package com.example.cloudrive.data.model

import com.google.gson.annotations.SerializedName

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int
)

data class User(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("username") val username: String,
    @SerializedName("quota_bytes") val quotaBytes: Long,
    @SerializedName("used_bytes") val usedBytes: Long,
    @SerializedName("created_at") val createdAt: String
)

data class FileItem(
    @SerializedName("id") val id: Long,
    @SerializedName("filename") val filename: String,
    @SerializedName("size") val size: Long,
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("folder_id") val folderId: Long?,
    @SerializedName("thumb_ready") val thumbReady: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("deleted_at") val deletedAt: String?,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null
)

data class FileInfo(
    @SerializedName("id") val id: Long,
    @SerializedName("filename") val filename: String,
    @SerializedName("size") val size: Long,
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("folder_id") val folderId: Long?,
    @SerializedName("thumb_ready") val thumbReady: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("deleted_at") val deletedAt: String?,
    @SerializedName("url") val url: String,
    @SerializedName("local_url") val localUrl: String?
)

data class FileList(
    @SerializedName("page") val page: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("total") val total: Int,
    @SerializedName("items") val items: List<FileItem>
)

data class Folder(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("parent_id") val parentId: Long?,
    @SerializedName("created_at") val createdAt: String
)

data class BreadcrumbItem(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String
)

data class FolderContents(
    @SerializedName("folder") val folder: Folder,
    @SerializedName("breadcrumb") val breadcrumb: List<BreadcrumbItem>,
    @SerializedName("subfolders") val subfolders: List<Folder>,
    @SerializedName("files") val files: FileList
)

data class FolderList(
    @SerializedName("items") val items: List<Folder>
)

data class UploadSession(
    @SerializedName("upload_id") val uploadId: String,
    @SerializedName("filename") val filename: String? = null,
    @SerializedName("folder_id") val folderId: Long? = null,
    @SerializedName("offset") val offset: Long,
    @SerializedName("total_size") val totalSize: Long,
    @SerializedName("expires_at") val expiresAt: String
)

data class ResumableSessionsResponse(
    @SerializedName("sessions") val sessions: List<UploadSession>
)

data class ShareToken(
    @SerializedName("token") val token: String,
    @SerializedName("url") val url: String,
    @SerializedName("expires_at") val expiresAt: String?
)

data class ShareLinkItem(
    @SerializedName("token") val token: String,
    @SerializedName("url") val url: String,
    @SerializedName("file_id") val fileId: Long,
    @SerializedName("filename") val filename: String,
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("expires_at") val expiresAt: String?
)

data class ShareLinkListResponse(
    @SerializedName("links") val links: List<ShareLinkItem>
)

data class NetworkInfo(
    @SerializedName("local_addresses") val localAddresses: List<String>
)

data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class RegisterRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class LogoutRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class CreateFolderRequest(
    @SerializedName("name") val name: String,
    @SerializedName("parent_id") val parentId: Long?
)

data class RenameFileRequest(
    @SerializedName("filename") val filename: String?,
    @SerializedName("folder_id") val folderId: Long?
)

data class StartResumableRequest(
    @SerializedName("filename") val filename: String,
    @SerializedName("size") val size: Long,
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("folder_id") val folderId: Long?
)

data class StartResumableResponse(
    @SerializedName("upload_id") val uploadId: String,
    @SerializedName("offset") val offset: Long
)

data class ChunkResponse(
    @SerializedName("offset") val offset: Long
)

data class UploadResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("filename") val filename: String,
    @SerializedName("size") val size: Long,
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("url") val url: String,
    @SerializedName("local_url") val localUrl: String?
)

data class DeleteFileResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("deleted_at") val deletedAt: String
)

data class RestoreResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("status") val status: String
)

data class PurgeResponse(
    @SerializedName("purged") val purged: Int
)

data class BatchFilesRequest(
    @SerializedName("op") val op: String, // move | trash | restore | delete
    @SerializedName("ids") val ids: List<Long>,
    @SerializedName("folder_id") val folderId: Long? = null
)

data class BatchFailure(
    @SerializedName("id") val id: Long,
    @SerializedName("code") val code: String,
    @SerializedName("message") val message: String
)

data class BatchFilesResponse(
    @SerializedName("succeeded") val succeeded: List<Long>,
    @SerializedName("failed") val failed: List<BatchFailure>
)

data class ShareRequest(
    @SerializedName("expires_in") val expiresIn: Int?
)
