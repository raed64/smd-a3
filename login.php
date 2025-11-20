<?php
require_once "config.php";

$input = json_decode(file_get_contents("php://input"), true);

$email    = trim($input["email"] ?? "");
$password = trim($input["password"] ?? "");

if ($email === "" || $password === "") {
    echo json_encode(["success" => false, "message" => "Missing email or password"]);
    exit;
}

$stmt = $conn->prepare("SELECT id, username, email, password_hash, first_name, last_name, dob
                        FROM users WHERE email = ?");
$stmt->bind_param("s", $email);
$stmt->execute();
$result = $stmt->get_result();
$user = $result->fetch_assoc();

if (!$user || $password !== $user["password_hash"]) {
    echo json_encode(["success" => false, "message" => "Invalid credentials"]);
    $stmt->close();
    $conn->close();
    exit;
}

echo json_encode([
    "success" => true,
    "user" => [
        "id" => $user["id"],
        "username" => $user["username"],
        "email" => $user["email"],
        "first_name" => $user["first_name"],
        "last_name" => $user["last_name"],
        "dob" => $user["dob"]
    ]
]);
$stmt->close();
$conn->close();
?>
