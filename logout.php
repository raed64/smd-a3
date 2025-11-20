<?php
require_once "config.php";

header("Content-Type: application/json");

// Read JSON body
$input = json_decode(file_get_contents("php://input"), true);
$user_id = intval($input["user_id"] ?? 0);

// 1) Validate input
if ($user_id <= 0) {
    echo json_encode([
        "success" => false,
        "message" => "Invalid or missing user_id"
    ]);
    exit;
}

// 2) Check if user exists
$stmt = $conn->prepare("SELECT id FROM users WHERE id = ?");
$stmt->bind_param("i", $user_id);
$stmt->execute();
$result = $stmt->get_result();

if ($result && $result->num_rows === 1) {
    // (Optional) If you really want to clear a token and you have such a column:
    // $stmt2 = $conn->prepare("UPDATE users SET auth_token = NULL WHERE id = ?");
    // $stmt2->bind_param("i", $user_id);
    // $stmt2->execute();
    // $stmt2->close();

    echo json_encode([
        "success" => true,
        "message" => "Logout successful"
    ]);
} else {
    echo json_encode([
        "success" => false,
        "message" => "User not found"
    ]);
}

$stmt->close();
$conn->close();
