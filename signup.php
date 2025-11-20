<?php
require_once "config.php";

$input = json_decode(file_get_contents("php://input"), true);

$username   = trim($input["username"] ?? "");
$email      = trim($input["email"] ?? "");
$password   = trim($input["password"] ?? "");
$firstName  = trim($input["first_name"] ?? "");
$lastName   = trim($input["last_name"] ?? "");
$dob        = trim($input["dob"] ?? "");

if ($username === "" || $email === "" || $password === "") {
    echo json_encode(["success" => false, "message" => "Missing required fields"]);
    exit;
}

// Check if email or username exists
$stmt = $conn->prepare("SELECT id FROM users WHERE email = ? OR username = ?");
$stmt->bind_param("ss", $email, $username);
$stmt->execute();
$stmt->store_result();

if ($stmt->num_rows > 0) {
    echo json_encode(["success" => false, "message" => "Email or username already exists"]);
    $stmt->close();
    $conn->close();
    exit;
}
$stmt->close();

// Insert user
$stmt = $conn->prepare("INSERT INTO users (username, email, password_hash, first_name, last_name, dob)
                        VALUES (?, ?, ?, ?, ?, ?)");
$stmt->bind_param("ssssss", $username, $email, $password, $firstName, $lastName, $dob);

if ($stmt->execute()) {
    $userId = $stmt->insert_id;
    echo json_encode([
        "success" => true,
        "user" => [
            "id" => $userId,
            "username" => $username,
            "email" => $email,
            "first_name" => $firstName,
            "last_name" => $lastName,
            "dob" => $dob
        ]
    ]);
} else {
    echo json_encode(["success" => false, "message" => "Signup failed"]);
}
$stmt->close();
$conn->close();
?>
