<?php
require_once 'config.php';
header('Content-Type: application/json');

$action = $_POST['action'] ?? $_GET['action'] ?? '';

// 1. Heartbeat: Update last_active to NOW
if ($action === 'heartbeat') {
    $userId = $_POST['userId'] ?? 0;
    if ($userId > 0) {
        $now = (int)(microtime(true) * 1000);
        $stmt = $conn->prepare("UPDATE users SET last_active = ? WHERE id = ?");
        $stmt->bind_param("si", $now, $userId);
        $stmt->execute();
        echo json_encode(['success' => true]);
    } else {
        echo json_encode(['success' => false]);
    }
}

// 2. Go Offline: Explicitly set last_active to 0 (Instant Offline)
elseif ($action === 'go_offline') {
    $userId = $_POST['userId'] ?? 0;
    if ($userId > 0) {
        $stmt = $conn->prepare("UPDATE users SET last_active = 0 WHERE id = ?");
        $stmt->bind_param("i", $userId);
        $stmt->execute();
        echo json_encode(['success' => true]);
    } else {
        echo json_encode(['success' => false]);
    }
}

// 3. Get Status
elseif ($action === 'get_status') {
    $idsParam = $_GET['userIds'] ?? '';
    
    if (empty($idsParam)) {
        echo json_encode(['success' => true, 'statuses' => []]);
        exit;
    }

    $idsArray = explode(',', $idsParam);
    $safeIds = array_map('intval', $idsArray);
    $idsStr = implode(',', $safeIds);

    if (empty($idsStr)) {
        echo json_encode(['success' => true, 'statuses' => []]);
        exit;
    }

    $sql = "SELECT id, last_active FROM users WHERE id IN ($idsStr)";
    $result = $conn->query($sql);

    $statuses = [];
    $now = (int)(microtime(true) * 1000);
    $onlineThreshold = 30 * 1000; // 30 seconds timeout fallback

    while ($row = $result->fetch_assoc()) {
        $lastActive = (double)$row['last_active'];
        
        // If last_active is 0 (explicit offline) or older than 30s -> Offline
        if ($lastActive == 0) {
            $isOnline = false;
        } else {
            $diff = $now - $lastActive;
            $isOnline = ($diff < $onlineThreshold);
        }
        
        $statuses[] = [
            'uid' => (string)$row['id'],
            'status' => $isOnline ? 'online' : 'offline',
            'lastActive' => $lastActive
        ];
    }

    echo json_encode(['success' => true, 'statuses' => $statuses]);
}
?>