<?php
// posts.php
require_once 'config.php';

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

$method = $_SERVER['REQUEST_METHOD'];

switch ($method) {
    case 'GET':
        handle_get_posts($conn);
        break;
    case 'POST':
        handle_post_upload($conn);
        break;
    default:
        http_response_code(405);
        echo json_encode(['error' => 'Method not allowed']);
        break;
}

/**
 * GET /posts.php?userId=abc
 * Returns a list of posts with like info for this user.
 */
function handle_get_posts(mysqli $conn): void {
    $userId = trim($_GET['userId'] ?? '');

    // Even if userId empty, we still return posts; likedByUser will be false.
    $nowUserId = $userId !== '' ? $userId : null;

    if ($nowUserId !== null) {
        $sql = "SELECT 
                    p.id,
                    p.user_id,
                    p.username,
                    p.user_profile_image_url,
                    p.media_url,
                    p.caption,
                    p.likes_count,
                    p.comments_count,
                    p.created_at,
                    IF(pl.user_id IS NULL, 0, 1) AS likedByUser
                FROM posts p
                LEFT JOIN post_likes pl 
                    ON pl.post_id = p.id AND pl.user_id = ?
                ORDER BY p.created_at DESC";
        $stmt = $conn->prepare($sql);
        if (!$stmt) {
            http_response_code(500);
            echo json_encode(['error' => 'DB prepare failed', 'details' => $conn->error]);
            return;
        }
        $stmt->bind_param('s', $nowUserId);
    } else {
        $sql = "SELECT 
                    p.id,
                    p.user_id,
                    p.username,
                    p.user_profile_image_url,
                    p.media_url,
                    p.caption,
                    p.likes_count,
                    p.comments_count,
                    p.created_at,
                    0 AS likedByUser
                FROM posts p
                ORDER BY p.created_at DESC";
        $stmt = $conn->prepare($sql);
        if (!$stmt) {
            http_response_code(500);
            echo json_encode(['error' => 'DB prepare failed', 'details' => $conn->error]);
            return;
        }
    }

    $stmt->execute();
    $result = $stmt->get_result();

    $posts = [];
    while ($row = $result->fetch_assoc()) {
        $posts[] = [
            'id' => (string)$row['id'],
            'userId' => $row['user_id'],
            'username' => $row['username'],
            'userProfileImageUrl' => $row['user_profile_image_url'] ?? '',
            'mediaUrl' => $row['media_url'],
            'caption' => $row['caption'] ?? '',
            'likesCount' => (int)$row['likes_count'],
            'commentsCount' => (int)$row['comments_count'],
            'createdAt' => (int)$row['created_at'],
            'likedByUser' => $row['likedByUser'] == 1
        ];
    }

    echo json_encode($posts);
    $stmt->close();
}

/**
 * POST /posts.php
 * Multipart form:
 *  - media (file)
 *  - userId
 *  - username
 *  - caption
 *  - createdAt (millis)
 *  - userProfileImageUrl (optional)
 */
function handle_post_upload(mysqli $conn): void {
    if (!isset($_FILES['media'])) {
        http_response_code(400);
        echo json_encode(['error' => 'Missing media file']);
        return;
    }

    $userId  = trim($_POST['userId'] ?? '');
    $username = trim($_POST['username'] ?? '');
    $caption = trim($_POST['caption'] ?? '');
    $createdAt = isset($_POST['createdAt']) ? (int)$_POST['createdAt'] : 0;
    $userProfileImageUrl = trim($_POST['userProfileImageUrl'] ?? '');

    if ($userId === '' || $username === '' || $createdAt <= 0) {
        http_response_code(400);
        echo json_encode(['error' => 'Missing or invalid fields']);
        return;
    }

    // Upload dir
    $uploadDir = __DIR__ . '/uploads/posts/';
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
        $ext = 'jpg';
    }

    $uniqueName = 'post_' . time() . '_' . bin2hex(random_bytes(4)) . '.' . $ext;
    $targetPath = $uploadDir . $uniqueName;

    if (!move_uploaded_file($file['tmp_name'], $targetPath)) {
        http_response_code(500);
        echo json_encode(['error' => 'Failed to move uploaded file']);
        return;
    }

    $baseUrl = get_base_url();
    $mediaUrl = $baseUrl . '/uploads/posts/' . $uniqueName;

    $sql = "INSERT INTO posts (user_id, username, user_profile_image_url, media_url, caption, likes_count, comments_count, created_at)
            VALUES (?, ?, ?, ?, ?, 0, 0, ?)";

    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        http_response_code(500);
        echo json_encode(['error' => 'DB prepare failed', 'details' => $conn->error]);
        return;
    }

    $stmt->bind_param(
        'sssssi',
        $userId,
        $username,
        $userProfileImageUrl,
        $mediaUrl,
        $caption,
        $createdAt
    );

    if (!$stmt->execute()) {
        http_response_code(500);
        echo json_encode(['error' => 'DB insert failed', 'details' => $stmt->error]);
        return;
    }

    $newId = $stmt->insert_id;

    $post = [
        'id' => (string)$newId,
        'userId' => $userId,
        'username' => $username,
        'userProfileImageUrl' => $userProfileImageUrl,
        'mediaUrl' => $mediaUrl,
        'caption' => $caption,
        'likesCount' => 0,
        'commentsCount' => 0,
        'createdAt' => $createdAt,
        'likedByUser' => false
    ];

    http_response_code(201);
    echo json_encode($post);
    $stmt->close();
}

function get_base_url(): string {
    $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    $host = $_SERVER['HTTP_HOST'] ?? 'localhost';
    $scriptName = $_SERVER['SCRIPT_NAME'] ?? '';
    $dir = rtrim(str_replace('\\', '/', dirname($scriptName)), '/');
    return $scheme . '://' . $host . $dir;
}
