<?php
// socially_api/users.php
require_once 'config.php';

$action = $_GET['action'] ?? '';
$currentUserId = $_GET['current_user_id'] ?? 0;

if ($action === 'get_all') {
    // Fetch users excluding the current user
    // Limit to 50 to prevent huge payloads
    $sql = "SELECT id, username, email, first_name, last_name, dob, profile_image_url 
            FROM users 
            WHERE id != ? 
            ORDER BY created_at DESC 
            LIMIT 50";
            
    $stmt = $conn->prepare($sql);
    $stmt->bind_param("i", $currentUserId);
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
} else {
    echo json_encode(["success" => false, "message" => "Invalid action"]);
}
?>