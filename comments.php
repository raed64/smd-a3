<?php
// comments.php
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
        handle_get_comments($conn);
        break;
    case 'POST':
        handle_post_comment($conn);
        break;
    default:
        http_response_code(405);
        echo json_encode(['error' => 'Method not allowed']);
        break;
}

/**
 * GET /comments.php?postId=123
 */
function handle_get_comments(mysqli $conn): void {
    $postId = isset($_GET['postId']) ? (int)$_GET['postId'] : 0;
    if ($postId <= 0) {
        http_response_code(400);
        echo json_encode(['error' => 'Invalid postId']);
        return;
    }

    $sql = "SELECT id, post_id, user_id, username, user_profile_image_url, text, created_at
            FROM post_comments
            WHERE post_id = ?
            ORDER BY created_at ASC";
    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        http_response_code(500);
        echo json_encode(['error' => 'DB prepare failed', 'details' => $conn->error]);
        return;
    }

    $stmt->bind_param('i', $postId);
    $stmt->execute();
    $result = $stmt->get_result();

    $comments = [];
    while ($row = $result->fetch_assoc()) {
        $comments[] = [
            'id' => (string)$row['id'],
            'postId' => (int)$row['post_id'],
            'userId' => $row['user_id'],
            'username' => $row['username'],
            'userProfileImageUrl' => $row['user_profile_image_url'] ?? '',
            'text' => $row['text'],
            'createdAt' => (int)$row['created_at']
        ];
    }

    echo json_encode($comments);
    $stmt->close();
}

/**
 * POST /comments.php
 * Fields:
 * - postId
 * - userId
 * - username
 * - userProfileImageUrl (optional)
 * - text
 * - createdAt
 */
function handle_post_comment(mysqli $conn): void {
    $postId = isset($_POST['postId']) ? (int)$_POST['postId'] : 0;
    $userId = trim($_POST['userId'] ?? '');
    $username = trim($_POST['username'] ?? '');
    $userProfileImageUrl = trim($_POST['userProfileImageUrl'] ?? '');
    $text = trim($_POST['text'] ?? '');
    $createdAt = isset($_POST['createdAt']) ? (int)$_POST['createdAt'] : 0;

    if ($postId <= 0 || $userId === '' || $username === '' || $text === '' || $createdAt <= 0) {
        http_response_code(400);
        echo json_encode(['error' => 'Missing or invalid fields']);
        return;
    }

    $sql = "INSERT INTO post_comments (post_id, user_id, username, user_profile_image_url, text, created_at)
            VALUES (?, ?, ?, ?, ?, ?)";
    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        http_response_code(500);
        echo json_encode(['error' => 'DB prepare failed', 'details' => $conn->error]);
        return;
    }

    $stmt->bind_param(
        'issssi',
        $postId,
        $userId,
        $username,
        $userProfileImageUrl,
        $text,
        $createdAt
    );

    if (!$stmt->execute()) {
        http_response_code(500);
        echo json_encode(['error' => 'DB insert failed', 'details' => $stmt->error]);
        return;
    }

    $newId = $stmt->insert_id;
    $stmt->close();

    // Update posts.comments_count
    $sqlCount = "SELECT COUNT(*) AS cnt FROM post_comments WHERE post_id = ?";
    $stmtCount = $conn->prepare($sqlCount);
    if ($stmtCount) {
        $stmtCount->bind_param('i', $postId);
        $stmtCount->execute();
        $res = $stmtCount->get_result();
        $row = $res->fetch_assoc();
        $commentsCount = (int)$row['cnt'];
        $stmtCount->close();

        $sqlUpdate = "UPDATE posts SET comments_count = ? WHERE id = ?";
        $stmtUpdate = $conn->prepare($sqlUpdate);
        if ($stmtUpdate) {
            $stmtUpdate->bind_param('ii', $commentsCount, $postId);
            $stmtUpdate->execute();
            $stmtUpdate->close();
        }
    }

    $comment = [
        'id' => (string)$newId,
        'postId' => $postId,
        'userId' => $userId,
        'username' => $username,
        'userProfileImageUrl' => $userProfileImageUrl,
        'text' => $text,
        'createdAt' => $createdAt
    ];

    http_response_code(201);
    echo json_encode($comment);
}
