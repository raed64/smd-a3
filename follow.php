<?php
require_once 'config.php';
require_once 'fcm.php';

header('Content-Type: application/json');

// Helper to log errors and exit
function sendError($message, $details = null) {
    echo json_encode(['success' => false, 'message' => $message, 'details' => $details]);
    exit;
}

$method = $_SERVER['REQUEST_METHOD'];
$action = $_POST['action'] ?? $_GET['action'] ?? '';
$userId = $_POST['userId'] ?? $_GET['userId'] ?? ''; 
$targetId = $_POST['targetId'] ?? $_GET['targetId'] ?? '';

// --- POST ACTIONS (Write) ---
if ($method === 'POST') {
    if (empty($userId) || empty($targetId)) {
        sendError('Missing userId or targetId');
    }

    // 1. Send Follow Request
    if ($action === 'follow_request') {
        // Create table if missing (Safety check)
        $conn->query("CREATE TABLE IF NOT EXISTS follows (
            id INT AUTO_INCREMENT PRIMARY KEY,
            follower_id INT NOT NULL,
            followed_id INT NOT NULL,
            status INT DEFAULT 0 COMMENT '0=Pending, 1=Accepted',
            created_at BIGINT,
            UNIQUE KEY unique_follow (follower_id, followed_id),
            FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY (followed_id) REFERENCES users(id) ON DELETE CASCADE
        )");

        $stmt = $conn->prepare("INSERT IGNORE INTO follows (follower_id, followed_id, status, created_at) VALUES (?, ?, 0, ?)");
        $now = (int)(microtime(true)*1000);
        $stmt->bind_param("iii", $userId, $targetId, $now);
        
        if ($stmt->execute()) {
            if ($stmt->affected_rows > 0) {
                // Notify Target
                $stmtT = $conn->prepare("SELECT fcm_token FROM users WHERE id = ?");
                $stmtT->bind_param("i", $targetId);
                $stmtT->execute();
                $res = $stmtT->get_result()->fetch_assoc();
                
                // Get Sender Name
                $stmtS = $conn->prepare("SELECT username FROM users WHERE id = ?");
                $stmtS->bind_param("i", $userId);
                $stmtS->execute();
                $senderName = $stmtS->get_result()->fetch_assoc()['username'] ?? "Someone";

                if ($res && !empty($res['fcm_token'])) {
                    send_fcm_notification($res['fcm_token'], "New Request", "$senderName wants to follow you", ['type' => 'follow_request']);
                }
                echo json_encode(['success' => true]);
            } else {
                // Request likely already exists (INSERT IGNORE didn't insert)
                echo json_encode(['success' => false, 'message' => 'Request already sent']);
            }
        } else {
            sendError('Database Insert Error: ' . $stmt->error);
        }
    } 
    
    // 2. Accept Follow Request
    elseif ($action === 'accept') {
        // NOTE: In 'accept', $userId is the person accepting (followed_id), $targetId is the requester (follower_id)
        $stmt = $conn->prepare("UPDATE follows SET status = 1 WHERE follower_id = ? AND followed_id = ?");
        $stmt->bind_param("ii", $targetId, $userId);
        
        if ($stmt->execute()) {
            echo json_encode(['success' => true]);
        } else {
            sendError('Accept Failed: ' . $stmt->error);
        }
    }
    
    // 3. Reject Request OR Unfollow
    elseif ($action === 'reject' || $action === 'unfollow') {
        $sql = "DELETE FROM follows WHERE follower_id = ? AND followed_id = ?";
        $stmt = $conn->prepare($sql);
        
        if ($action === 'reject') {
            // I am rejecting someone who wants to follow me
            // follower = targetId, followed = userId (me)
            $stmt->bind_param("ii", $targetId, $userId);
        } else {
            // I am unfollowing someone
            // follower = userId (me), followed = targetId
            $stmt->bind_param("ii", $userId, $targetId);
        }
        
        if ($stmt->execute()) {
            echo json_encode(['success' => true]);
        } else {
            sendError('Delete Failed: ' . $stmt->error);
        }
    }
}

// --- GET ACTIONS (Read) ---
elseif ($method === 'GET') {
    
    if ($action === 'followers') {
        // Get list of people following ME (userId)
        $sql = "SELECT u.id, u.username, u.first_name, u.last_name, u.profile_image_url 
                FROM follows f JOIN users u ON f.follower_id = u.id 
                WHERE f.followed_id = ? AND f.status = 1";
        $stmt = $conn->prepare($sql);
        $stmt->bind_param("i", $userId);
        $stmt->execute();
        $result = $stmt->get_result();
        echo json_encode(['success' => true, 'users' => $result->fetch_all(MYSQLI_ASSOC)]);
    }
    
    elseif ($action === 'following') {
        // Get list of people I (userId) am following
        $sql = "SELECT u.id, u.username, u.first_name, u.last_name, u.profile_image_url 
                FROM follows f JOIN users u ON f.followed_id = u.id 
                WHERE f.follower_id = ? AND f.status = 1";
        $stmt = $conn->prepare($sql);
        $stmt->bind_param("i", $userId);
        $stmt->execute();
        $result = $stmt->get_result();
        echo json_encode(['success' => true, 'users' => $result->fetch_all(MYSQLI_ASSOC)]);
    }
    
    elseif ($action === 'requests') {
        // Get list of pending requests FOR ME (userId)
        // People who want to follow me -> follower_id = THEM, followed_id = ME
        $sql = "SELECT u.id, u.username, u.first_name, u.last_name, u.profile_image_url 
                FROM follows f JOIN users u ON f.follower_id = u.id 
                WHERE f.followed_id = ? AND f.status = 0";
        $stmt = $conn->prepare($sql);
        $stmt->bind_param("i", $userId);
        $stmt->execute();
        $result = $stmt->get_result();
        echo json_encode(['success' => true, 'users' => $result->fetch_all(MYSQLI_ASSOC)]);
    }
    
    elseif ($action === 'check_status') {
        // Check relation: Do *I* ($userId) follow *THEM* ($targetId)?
        $targetId = $_GET['targetId'] ?? '';
        $stmt = $conn->prepare("SELECT status FROM follows WHERE follower_id = ? AND followed_id = ?");
        $stmt->bind_param("ii", $userId, $targetId);
        $stmt->execute();
        $res = $stmt->get_result();
        
        $status = 'none'; 
        if ($row = $res->fetch_assoc()) {
            $status = ($row['status'] == 1) ? 'following' : 'pending';
        }
        echo json_encode(['success' => true, 'status' => $status]);
    }
}
?>