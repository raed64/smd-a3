<?php
require_once 'config.php';
header('Content-Type: application/json');

$userId = $_POST['user_id'] ?? '';
$token = $_POST['token'] ?? '';

if ($userId && $token) {
    $stmt = $conn->prepare("UPDATE users SET fcm_token = ? WHERE id = ?");
    $stmt->bind_param("si", $token, $userId);
    
    if ($stmt->execute()) {
        echo json_encode(['success' => true]);
    } else {
        echo json_encode(['success' => false]);
    }
} else {
    echo json_encode(['success' => false]);
}
?>