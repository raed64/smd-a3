<?php
require_once 'config.php';
header('Content-Type: application/json');

$userId = $_POST['user_id'] ?? '';
$username = $_POST['username'] ?? '';
$firstName = $_POST['first_name'] ?? '';
$lastName = $_POST['last_name'] ?? '';
$email = $_POST['email'] ?? '';
$bio = $_POST['bio'] ?? ''; // Added Bio

if (empty($userId)) {
    echo json_encode(['success' => false, 'message' => 'User ID required']);
    exit;
}

// Updated SQL to include bio
$stmt = $conn->prepare("UPDATE users SET username=?, first_name=?, last_name=?, email=?, bio=? WHERE id=?");
$stmt->bind_param("sssssi", $username, $firstName, $lastName, $email, $bio, $userId);

if ($stmt->execute()) {
    echo json_encode(['success' => true]);
} else {
    echo json_encode(['success' => false, 'message' => 'Update failed']);
}
?>