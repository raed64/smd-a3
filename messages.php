<?php
// socially_api/messages.php
require_once 'config.php';

header('Content-Type: application/json; charset=utf-8');

$method = $_SERVER['REQUEST_METHOD'];

if ($method === 'GET') {
    handle_get_messages($conn);
} 
else if ($method === 'POST') {
    $action = $_POST['action'] ?? 'send_message';
    
    if ($action === 'toggle_vanish') {
        handle_toggle_vanish($conn);
    } else {
        handle_send_message($conn);
    }
}
else if ($method === 'PUT') {
    handle_edit_message($conn);
}
else if ($method === 'DELETE') {
    handle_delete_message($conn);
}
else {
    echo json_encode(['error' => 'Invalid method']);
}

function handle_get_messages($conn) {
    $user1 = $_GET['user1'] ?? '';
    $user2 = $_GET['user2'] ?? '';
    
    $chatId = get_chat_id($conn, $user1, $user2);
    
    if (!$chatId) {
        echo json_encode([
            "success" => true, 
            "vanish_mode" => false, 
            "messages" => []
        ]); 
        return;
    }

    $chatSql = "SELECT vanish_mode FROM chats WHERE id = ?";
    $stmtC = $conn->prepare($chatSql);
    $stmtC->bind_param("i", $chatId);
    $stmtC->execute();
    $chatRes = $stmtC->get_result()->fetch_assoc();
    
    $isVanish = ($chatRes && $chatRes['vanish_mode'] == 1);

    $sql = "SELECT * FROM messages WHERE chat_id = ? ORDER BY created_at ASC";
    $stmt = $conn->prepare($sql);
    $stmt->bind_param("i", $chatId);
    $stmt->execute();
    $result = $stmt->get_result();
    
    $messages = [];
    while ($row = $result->fetch_assoc()) {
        $row['id'] = (int)$row['id'];
        $row['chat_id'] = (int)$row['chat_id'];
        $row['created_at'] = (int)$row['created_at'];
        $row['edited_at'] = (int)$row['edited_at'];
        $row['is_deleted'] = (int)$row['is_deleted'];
        $messages[] = $row;
    }
    
    echo json_encode([
        "success" => true,
        "vanish_mode" => $isVanish,
        "messages" => $messages
    ]);
}

function handle_toggle_vanish($conn) {
    $user1 = $_POST['user1'] ?? '';
    $user2 = $_POST['user2'] ?? '';
    $enable = $_POST['enable'] ?? '0'; 
    
    $chatId = get_or_create_chat($conn, $user1, $user2);
    $val = ($enable === 'true' || $enable === '1') ? 1 : 0;
    
    $sql = "UPDATE chats SET vanish_mode = ? WHERE id = ?";
    $stmt = $conn->prepare($sql);
    $stmt->bind_param("ii", $val, $chatId);
    
    if ($stmt->execute()) {
        echo json_encode(["success" => true, "vanish_mode" => ($val == 1)]);
    } else {
        http_response_code(500);
        echo json_encode(["success" => false, "message" => "Failed to update vanish mode"]);
    }
}

function handle_send_message($conn) {
    $senderId = $_POST['senderId'] ?? '';
    $receiverId = $_POST['receiverId'] ?? '';
    $text = $_POST['text'] ?? '';
    $type = $_POST['type'] ?? 'TEXT';
    $createdAt = $_POST['createdAt'] ?? (int)(microtime(true) * 1000);
    $postId = $_POST['postId'] ?? '';
    
    $chatId = get_or_create_chat($conn, $senderId, $receiverId);
    
    $mediaUrl = "";
    
    if (isset($_FILES['media']) && $_FILES['media']['error'] === UPLOAD_ERR_OK) {
        $uploadDir = __DIR__ . '/uploads/messages/';
        if (!is_dir($uploadDir)) {
            mkdir($uploadDir, 0777, true);
        }
        
        $ext = pathinfo($_FILES['media']['name'], PATHINFO_EXTENSION);
        if (!$ext) $ext = 'bin';
        
        $filename = 'msg_' . time() . '_' . mt_rand(1000,9999) . '.' . $ext;
        $target = $uploadDir . $filename;
        
        if (move_uploaded_file($_FILES['media']['tmp_name'], $target)) {
             $mediaUrl = get_base_url() . '/uploads/messages/' . $filename;
        }
    }

    $sql = "INSERT INTO messages (chat_id, sender_id, type, text_content, media_url, post_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
    $stmt = $conn->prepare($sql);
    $stmt->bind_param("issssss", $chatId, $senderId, $type, $text, $mediaUrl, $postId, $createdAt);
    
    if ($stmt->execute()) {
        $newId = $stmt->insert_id;
        
        $lastMsgText = ($type == 'IMAGE') ? 'Photo' : (($type == 'POST_SHARE') ? 'Shared a post' : $text);
        if ($type == 'VANISH' || $type == 'VANISH_IMAGE') {
            $lastMsgText = "Vanish message";
        }
        
        $updateChat = $conn->prepare("UPDATE chats SET last_message_text = ?, last_message_time = ? WHERE id = ?");
        $updateChat->bind_param("sii", $lastMsgText, $createdAt, $chatId);
        $updateChat->execute();

        // --- START NOTIFICATION LOGIC ---
        require_once 'fcm.php';
        
        // 1. Get Receiver's Token
        $stmtT = $conn->prepare("SELECT fcm_token FROM users WHERE id = ?");
        $stmtT->bind_param("i", $receiverId);
        $stmtT->execute();
        $recData = $stmtT->get_result()->fetch_assoc();

        if ($recData && !empty($recData['fcm_token'])) {
            // 2. Get Sender's Name
            $stmtS = $conn->prepare("SELECT username FROM users WHERE id = ?");
            $stmtS->bind_param("i", $senderId);
            $stmtS->execute();
            $senderName = $stmtS->get_result()->fetch_assoc()['username'] ?? 'User';

            $title = $senderName;
            $body = ($type == 'IMAGE') ? 'Sent a photo' : $text;
            
            $data = [
                'type' => 'message',
                'senderId' => $senderId,
                'senderName' => $senderName,
                'messageText' => $body
            ];
            
            // 3. Send Notification
            send_fcm_notification($recData['fcm_token'], $title, $body, $data);
        }
        // --- END NOTIFICATION LOGIC ---

        echo json_encode(['success' => true, 'id' => $newId, 'mediaUrl' => $mediaUrl]);
    } else {
        http_response_code(500);
        echo json_encode(['error' => 'Failed to save message']);
    }
}

function handle_edit_message($conn) {
    $input = file_get_contents("php://input");
    $data = json_decode($input, true);
    
    $msgId = $data['id'] ?? 0;
    $newText = $data['text'] ?? '';
    $senderId = $data['senderId'] ?? '';

    $checkSql = "SELECT created_at, sender_id FROM messages WHERE id = ?";
    $stmtCheck = $conn->prepare($checkSql);
    $stmtCheck->bind_param("i", $msgId);
    $stmtCheck->execute();
    $res = $stmtCheck->get_result()->fetch_assoc();
    
    if (!$res) {
        echo json_encode(['error' => 'Message not found']); return;
    }
    if ($res['sender_id'] !== $senderId) {
        echo json_encode(['error' => 'Unauthorized']); return;
    }
    
    $diff = (microtime(true)*1000) - $res['created_at'];
    if ($diff > 300000) { 
        echo json_encode(['error' => 'Too late to edit (5 min limit)']); return;
    }

    $updateSql = "UPDATE messages SET text_content = ?, edited_at = ? WHERE id = ?";
    $now = (int)(microtime(true) * 1000);
    $stmt = $conn->prepare($updateSql);
    $stmt->bind_param("sii", $newText, $now, $msgId);
    
    if ($stmt->execute()) {
        echo json_encode(['success' => true]);
    } else {
        echo json_encode(['error' => 'Update failed']);
    }
}

function handle_delete_message($conn) {
    $input = file_get_contents("php://input");
    $data = json_decode($input, true);
    
    $msgId = $data['id'] ?? 0;
    $senderId = $data['senderId'] ?? '';

    $checkSql = "SELECT created_at, sender_id FROM messages WHERE id = ?";
    $stmtCheck = $conn->prepare($checkSql);
    $stmtCheck->bind_param("i", $msgId);
    $stmtCheck->execute();
    $res = $stmtCheck->get_result()->fetch_assoc();
    
    if (!$res) {
        echo json_encode(['error' => 'Message not found']); return;
    }
    if ($res['sender_id'] !== $senderId) {
        echo json_encode(['error' => 'Unauthorized']); return;
    }

    $diff = (microtime(true)*1000) - $res['created_at'];
    if ($diff > 300000) { 
        echo json_encode(['error' => 'Too late to delete (5 min limit)']); return;
    }

    $delSql = "UPDATE messages SET is_deleted = 1, text_content = '', media_url = '' WHERE id = ?";
    $stmt = $conn->prepare($delSql);
    $stmt->bind_param("i", $msgId);
    
    if ($stmt->execute()) {
        echo json_encode(['success' => true]);
    } else {
        echo json_encode(['error' => 'Delete failed']);
    }
}

function get_chat_id($conn, $u1, $u2) {
    if (strcmp($u1, $u2) > 0) { 
        $temp = $u1; $u1 = $u2; $u2 = $temp; 
    }
    
    $sql = "SELECT id FROM chats WHERE user_1_id = ? AND user_2_id = ?";
    $stmt = $conn->prepare($sql);
    $stmt->bind_param("ss", $u1, $u2);
    $stmt->execute();
    $res = $stmt->get_result()->fetch_assoc();
    return $res ? $res['id'] : null;
}

function get_or_create_chat($conn, $sender, $receiver) {
    $id = get_chat_id($conn, $sender, $receiver);
    if ($id) return $id;

    if (strcmp($sender, $receiver) > 0) { 
        $temp = $sender; $sender = $receiver; $receiver = $temp; 
    }
    
    $stmt = $conn->prepare("INSERT INTO chats (user_1_id, user_2_id, last_message_time, vanish_mode) VALUES (?, ?, ?, 0)");
    $now = (int)(microtime(true) * 1000);
    $stmt->bind_param("ssi", $sender, $receiver, $now);
    $stmt->execute();
    return $stmt->insert_id;
}

function get_base_url() {
    $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    $host = $_SERVER['HTTP_HOST'];
    $dir = dirname($_SERVER['SCRIPT_NAME']);
    $dir = str_replace('\\', '/', $dir);
    return $scheme . '://' . $host . $dir;
}
?>