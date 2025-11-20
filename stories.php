<?php
// stories.php
require_once 'config.php';

header('Content-Type: application/json; charset=utf-8');

// (optional) Allow from emulator / browser during dev:
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

// Handle preflight
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

$method = $_SERVER['REQUEST_METHOD'];

switch ($method) {
    case 'GET':
        handle_get_stories($conn);
        break;
    case 'POST':
        handle_post_story($conn);
        break;
    case 'DELETE':
        handle_delete_story($conn);
        break;
    default:
        http_response_code(405);
        echo json_encode(['error' => 'Method not allowed']);
        break;
}

/**
 * GET /stories.php
 * Returns all non-expired stories.
 */
function handle_get_stories(mysqli $conn): void {
    $now = (int) (microtime(true) * 1000);

    $sql = "SELECT id, user_id, username, user_profile_image_url, media_url, media_type, created_at, expires_at
            FROM stories
            WHERE expires_at > ?
            ORDER BY created_at DESC";

    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        http_response_code(500);
        echo json_encode(['error' => 'DB prepare failed', 'details' => $conn->error]);
        return;
    }

    $stmt->bind_param('i', $now);
    $stmt->execute();
    $result = $stmt->get_result();

    $stories = [];
    while ($row = $result->fetch_assoc()) {
        $stories[] = [
            'id' => (string)$row['id'],
            'userId' => $row['user_id'],
            'username' => $row['username'],
            'userProfileImageUrl' => $row['user_profile_image_url'] ?? '',
            'mediaUrl' => $row['media_url'],
            'mediaType' => $row['media_type'],
            'createdAt' => (int)$row['created_at'],
            'expiresAt' => (int)$row['expires_at'],
        ];
    }

    echo json_encode($stories);
    $stmt->close();
}

/**
 * POST /stories.php
 * Multipart form:
 *  - media (file)
 *  - userId
 *  - username
 *  - mediaType  (image|video)
 *  - createdAt  (Long millis)
 *  - expiresAt  (Long millis)
 *  - userProfileImageUrl (optional)
 */
function handle_post_story(mysqli $conn): void {
    // Basic validation
    if (!isset($_FILES['media'])) {
        http_response_code(400);
        echo json_encode(['error' => 'Missing media file']);
        return;
    }

    $userId  = trim($_POST['userId'] ?? '');
    $username = trim($_POST['username'] ?? '');
    $mediaType = trim($_POST['mediaType'] ?? 'image');
    $createdAt = isset($_POST['createdAt']) ? (int)$_POST['createdAt'] : 0;
    $expiresAt = isset($_POST['expiresAt']) ? (int)$_POST['expiresAt'] : 0;
    $userProfileImageUrl = trim($_POST['userProfileImageUrl'] ?? '');

    if ($userId === '' || $username === '' || $createdAt <= 0 || $expiresAt <= 0) {
        http_response_code(400);
        echo json_encode(['error' => 'Missing or invalid fields']);
        return;
    }

    // Upload file
    $uploadDir = __DIR__ . '/uploads/stories/';
    if (!is_dir($uploadDir)) {
        if (!mkdir($uploadDir, 0777, true) && !is_dir($uploadDir)) {
            http_response_code(500);
            echo json_encode(['error' => 'Failed to create upload directory']);
            return;
        }
    }

    $file = $_FILES['media'];
    if ($file['error'] !== UPLOAD_ERR_OK) {
        http_response_code(400);
        echo json_encode(['error' => 'File upload error', 'code' => $file['error']]);
        return;
    }

    $originalName = $file['name'];
    $ext = strtolower(pathinfo($originalName, PATHINFO_EXTENSION));
    if ($ext === '') {
        $ext = 'bin';
    }

    $uniqueName = 'story_' . time() . '_' . bin2hex(random_bytes(4)) . '.' . $ext;
    $targetPath = $uploadDir . $uniqueName;

    if (!move_uploaded_file($file['tmp_name'], $targetPath)) {
        http_response_code(500);
        echo json_encode(['error' => 'Failed to move uploaded file']);
        return;
    }

    // Build URL to the file (adjust base URL according to your setup)
    $baseUrl = get_base_url();
    $mediaUrl = $baseUrl . '/uploads/stories/' . $uniqueName;

    // Insert into DB
    $sql = "INSERT INTO stories (user_id, username, user_profile_image_url, media_url, media_type, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)";

    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        http_response_code(500);
        echo json_encode(['error' => 'DB prepare failed', 'details' => $conn->error]);
        return;
    }

    $stmt->bind_param(
        'ssssssi',
        $userId,
        $username,
        $userProfileImageUrl,
        $mediaUrl,
        $mediaType,
        $createdAt,
        $expiresAt
    );

    if (!$stmt->execute()) {
        http_response_code(500);
        echo json_encode(['error' => 'DB insert failed', 'details' => $stmt->error]);
        return;
    }

    $newId = $stmt->insert_id;

    $story = [
        'id' => (string)$newId,
        'userId' => $userId,
        'username' => $username,
        'userProfileImageUrl' => $userProfileImageUrl,
        'mediaUrl' => $mediaUrl,
        'mediaType' => $mediaType,
        'createdAt' => $createdAt,
        'expiresAt' => $expiresAt,
    ];

    http_response_code(201);
    echo json_encode($story);
    $stmt->close();
}

/**
 * DELETE /stories.php?id=123
 */
function handle_delete_story(mysqli $conn): void {
    $id = isset($_GET['id']) ? (int)$_GET['id'] : 0;
    if ($id <= 0) {
        http_response_code(400);
        echo json_encode(['error' => 'Invalid story id']);
        return;
    }

    // Optionally delete file from disk:
    $stmtSelect = $conn->prepare("SELECT media_url FROM stories WHERE id = ?");
    $stmtSelect->bind_param('i', $id);
    $stmtSelect->execute();
    $result = $stmtSelect->get_result();
    $mediaPathOnDisk = null;

    if ($row = $result->fetch_assoc()) {
        $mediaUrl = $row['media_url'];
        $mediaPathOnDisk = convert_url_to_path($mediaUrl);
    }
    $stmtSelect->close();

    $stmt = $conn->prepare("DELETE FROM stories WHERE id = ?");
    if (!$stmt) {
        http_response_code(500);
        echo json_encode(['error' => 'DB prepare failed', 'details' => $conn->error]);
        return;
    }

    $stmt->bind_param('i', $id);
    $stmt->execute();

    if ($stmt->affected_rows > 0) {
        if ($mediaPathOnDisk && file_exists($mediaPathOnDisk)) {
            @unlink($mediaPathOnDisk);
        }
        echo json_encode(['success' => true]);
    } else {
        http_response_code(404);
        echo json_encode(['error' => 'Story not found']);
    }

    $stmt->close();
}

/**
 * Build base URL from current request, e.g. http://localhost/social_api
 */
function get_base_url(): string {
    $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    $host = $_SERVER['HTTP_HOST'] ?? 'localhost';
    $scriptName = $_SERVER['SCRIPT_NAME'] ?? '';
    $dir = rtrim(str_replace('\\', '/', dirname($scriptName)), '/');
    return $scheme . '://' . $host . $dir;
}

/**
 * Convert a media URL back to a file path on disk (for deletion)
 * This assumes the URL points to /uploads/stories/ inside this folder.
 */
function convert_url_to_path(string $url): ?string {
    $parsed = parse_url($url);
    if (!isset($parsed['path'])) {
        return null;
    }
    $path = $parsed['path']; // e.g. /social_api/uploads/stories/story_xxx.jpg
    $relative = basename(dirname($path)) . '/' . basename($path); // uploads/stories/filename
    $fullPath = __DIR__ . '/' . $relative;
    return $fullPath;
}
