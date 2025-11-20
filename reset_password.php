<?php
header("Content-Type: application/json");
require_once "config.php"; // make sure this defines $conn (mysqli)

// 1) Check DB connection
if ($conn->connect_error) {
    http_response_code(500);
    echo json_encode([
        "success" => false,
        "message" => "DB connection failed: " . $conn->connect_error
    ]);
    exit;
}

// 2) Read JSON input
$input = file_get_contents("php://input");
$data = json_decode($input, true);

if (!$data || !isset($data['email'])) {
    echo json_encode([
        "success" => false,
        "message" => "Email required"
    ]);
    exit;
}

$email = $data['email'];

// 3) Check if user exists
// ⚠ Make sure table name and column name match your DB schema!
$query = "SELECT id FROM users WHERE email = ?";
$stmt = $conn->prepare($query);
if (!$stmt) {
    echo json_encode([
        "success" => false,
        "message" => "Prepare failed (SELECT): " . $conn->error
    ]);
    exit;
}

$stmt->bind_param("s", $email);
$stmt->execute();
$result = $stmt->get_result();

if ($result->num_rows === 0) {
    echo json_encode([
        "success" => false,
        "message" => "Email not registered"
    ]);
    $stmt->close();
    exit;
}
$stmt->close();

// 4) Generate and store reset token
$token = bin2hex(random_bytes(16));

// ⚠ Make sure column `reset_token` exists in `users` table:
//   ALTER TABLE users ADD COLUMN reset_token VARCHAR(64) NULL;
$updateQuery = "UPDATE users SET reset_token = ? WHERE email = ?";
$updateStmt = $conn->prepare($updateQuery);
if (!$updateStmt) {
    echo json_encode([
        "success" => false,
        "message" => "Prepare failed (UPDATE): " . $conn->error
    ]);
    exit;
}

$updateStmt->bind_param("ss", $token, $email);
$updateStmt->execute();

if ($updateStmt->affected_rows === 0) {
    echo json_encode([
        "success" => false,
        "message" => "Failed to update reset token"
    ]);
    $updateStmt->close();
    exit;
}

$updateStmt->close();
$conn->close();

// 5) Success response (for assignment, returning token is enough)
echo json_encode([
    "success" => true,
    "message" => "Reset token generated",
    "token_for_testing" => $token
]);
?>