<?php
// socially_api/search.php
require_once 'config.php';

$query = $_GET['query'] ?? '';
$currentUserId = $_GET['current_user_id'] ?? 0;

if (trim($query) === '') {
    echo json_encode(["success" => true, "users" => []]);
    exit;
}

$searchTerm = "%" . $query . "%";

// Search in username, first_name, or last_name
$sql = "SELECT id, username, email, first_name, last_name, dob, profile_image_url 
        FROM users 
        WHERE (username LIKE ? OR first_name LIKE ? OR last_name LIKE ?) 
        AND id != ?
        LIMIT 50";

$stmt = $conn->prepare($sql);
$stmt->bind_param("sssi", $searchTerm, $searchTerm, $searchTerm, $currentUserId);
$stmt->execute();
$result = $stmt->get_result();

$users = [];
while ($row = $result->fetch_assoc()) {
    $users[] = $row;
}

echo json_encode([
    "success" => true,
    "users" => $users
]);

$stmt->close();
?>