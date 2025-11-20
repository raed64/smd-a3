<?php
// likes.php
require_once 'config.php';

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['error' => 'Method not allowed']);
    exit;
}

$postId = trim($_POST['postId'] ?? '');
$userId = trim($_POST['userId'] ?? '');
$likeFlag = $_POST['like'] ?? '';

if ($postId === '' || $userId === '') {
    http_response_code(400);
    echo json_encode(['error' => 'Missing postId or userId']);
    exit;
}

$like = filter_var($likeFlag, FILTER_VALIDATE_BOOLEAN);

// Like = true  => insert like
// Like = false => remove like
if ($like) {
    $sql = "INSERT IGNORE INTO post_likes (post_id, user_id) VALUES (?, ?)";
    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        http_response_code(500);
        echo json_encode(['error' => 'DB prepare failed', 'details' => $conn->error]);
        exit;
    }
    $stmt->bind_param('is', $postId, $userId);
    if (!$stmt->execute()) {
        http_response_code(500);
        echo json_encode(['error' => 'DB insert failed', 'details' => $stmt->error]);
        exit;
    }
    $stmt->close();
} else {
    $sql = "DELETE FROM post_likes WHERE post_id = ? AND user_id = ?";
    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        http_response_code(500);
        echo json_encode(['error' => 'DB prepare failed', 'details' => $conn->error]);
        exit;
    }
    $stmt->bind_param('is', $postId, $userId);
    if (!$stmt->execute()) {
        http_response_code(500);
        echo json_encode(['error' => 'DB delete failed', 'details' => $stmt->error]);
        exit;
    }
    $stmt->close();
}

// Recalculate total likes
$sqlCount = "SELECT COUNT(*) AS cnt FROM post_likes WHERE post_id = ?";
$stmtCount = $conn->prepare($sqlCount);
if (!$stmtCount) {
    http_response_code(500);
    echo json_encode(['error' => 'DB prepare failed', 'details' => $conn->error]);
    exit;
}
$stmtCount->bind_param('i', $postId);
$stmtCount->execute();
$result = $stmtCount->get_result();
$row = $result->fetch_assoc();
$likesCount = (int)$row['cnt'];
$stmtCount->close();

// Update posts.likes_count
$sqlUpdate = "UPDATE posts SET likes_count = ? WHERE id = ?";
$stmtUpdate = $conn->prepare($sqlUpdate);
if ($stmtUpdate) {
    $stmtUpdate->bind_param('ii', $likesCount, $postId);
    $stmtUpdate->execute();
    $stmtUpdate->close();
}

// Did user like now?
$sqlLiked = "SELECT 1 FROM post_likes WHERE post_id = ? AND user_id = ? LIMIT 1";
$stmtLiked = $conn->prepare($sqlLiked);
if (!$stmtLiked) {
    http_response_code(500);
    echo json_encode(['error' => 'DB prepare failed', 'details' => $conn->error]);
    exit;
}
$stmtLiked->bind_param('is', $postId, $userId);
$stmtLiked->execute();
$resLiked = $stmtLiked->get_result();
$likedByUser = $resLiked->num_rows > 0;
$stmtLiked->close();

echo json_encode([
    'likesCount' => $likesCount,
    'likedByUser' => $likedByUser
]);
