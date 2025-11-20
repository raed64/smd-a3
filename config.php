<?php
$host = "localhost";
$user = "root";
$pass = "";
$db_name = "socially_db";

$conn = new mysqli($host, $user, $pass, $db_name);
if ($conn->connect_error) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "DB connection failed"]);
    exit;
}

header("Content-Type: application/json; charset=UTF-8");
?>
