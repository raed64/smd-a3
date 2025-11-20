<?php
require_once 'config.php';
require_once 'fcm.php';

header('Content-Type: application/json');

$callerId = $_POST['callerId'] ?? '';
$targetId = $_POST['targetId'] ?? '';
$callerName = $_POST['callerName'] ?? 'Unknown';
$callType = $_POST['callType'] ?? 'video'; // 'video' or 'voice'
$channelName = $_POST['channelName'] ?? '';

if (!$callerId || !$targetId || !$channelName) {
    echo json_encode(['success' => false, 'message' => 'Missing parameters']);
    exit;
}

// 1. Get Target's FCM Token
$stmt = $conn->prepare("SELECT fcm_token FROM users WHERE id = ?");
$stmt->bind_param("i", $targetId);
$stmt->execute();
$res = $stmt->get_result()->fetch_assoc();

if ($res && !empty($res['fcm_token'])) {
    $title = "Incoming " . ucfirst($callType) . " Call";
    $body = "$callerName is calling you...";
    
    $data = [
        'type' => 'call',
        'callType' => $callType,
        'channelName' => $channelName,
        'fromUserId' => $callerId,
        'fromUserName' => $callerName
    ];
    
    // 2. Send Notification
    send_fcm_notification($res['fcm_token'], $title, $body, $data);
    echo json_encode(['success' => true]);
} else {
    echo json_encode(['success' => false, 'message' => 'User not reachable']);
}
?>