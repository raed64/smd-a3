<?php
require_once 'config.php';
header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $userId = $_POST['userId'] ?? '';
    $type = $_POST['type'] ?? 'profile'; // 'profile' or 'cover'
    
    if (empty($userId) || !isset($_FILES['image'])) {
        echo json_encode(['success' => false, 'message' => 'Invalid request']);
        exit;
    }

    $uploadDir = __DIR__ . '/uploads/users/';
    if (!is_dir($uploadDir)) mkdir($uploadDir, 0777, true);

    $ext = pathinfo($_FILES['image']['name'], PATHINFO_EXTENSION);
    if (!$ext) $ext = 'jpg';
    
    // Unique filename: profile_123.jpg or cover_123.jpg
    $filename = $type . '_' . $userId . '_' . time() . '.' . $ext;
    $targetPath = $uploadDir . $filename;

    if (move_uploaded_file($_FILES['image']['tmp_name'], $targetPath)) {
        $fullUrl = get_base_url() . '/uploads/users/' . $filename;
        
        // Decide which column to update based on type
        $col = ($type === 'cover') ? 'cover_photo_url' : 'profile_image_url';
        
        $sql = "UPDATE users SET $col = ? WHERE id = ?";
        $stmt = $conn->prepare($sql);
        $stmt->bind_param("si", $fullUrl, $userId);
        
        if ($stmt->execute()) {
            echo json_encode(['success' => true, 'url' => $fullUrl]);
        } else {
            echo json_encode(['success' => false, 'message' => 'DB update failed']);
        }
    } else {
        echo json_encode(['success' => false, 'message' => 'Upload failed']);
    }
}

function get_base_url() {
    $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    $host = $_SERVER['HTTP_HOST'];
    $dir = dirname($_SERVER['SCRIPT_NAME']);
    $dir = str_replace('\\', '/', $dir);
    return $scheme . '://' . $host . $dir;
}
?>