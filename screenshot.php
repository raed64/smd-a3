<?php
require_once 'config.php';
require_once 'fcm.php';

header('Content-Type: application/json');

$senderId = $_POST['senderId'] ?? '';
$targetId = $_POST['targetId'] ?? '';
$senderName = $_POST['senderName'] ?? 'Someone';

if ($senderId && $targetId) {
    // Get Target's Token
    $stmt = $conn->prepare("SELECT fcm_token FROM users WHERE id = ?");
    $stmt->bind_param("i", $targetId);
    $stmt->execute();
    $res = $stmt->get_result()->fetch_assoc();
    
    if ($res && !empty($res['fcm_token'])) {
        $title = "Screenshot Alert";
        $body = "$senderName took a screenshot of your chat!";
        $data = ['type' => 'screenshot', 'otherUserName' => $senderName];
        
        send_fcm_notification($res['fcm_token'], $title, $body, $data);
        echo json_encode(['success' => true]);
    } else {
        echo json_encode(['success' => false]);
    }
} else {
    echo json_encode(['success' => false]);
}
?>