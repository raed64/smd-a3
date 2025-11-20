<?php
// socially_api/chats.php
require_once 'config.php';

if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    $userId = $_GET['userId'] ?? '';

    if (empty($userId)) {
        echo json_encode([]);
        exit;
    }

    // Fetch chats where the user is either user_1 or user_2
    // Join with users table to get the OTHER user's info
    $sql = "
        SELECT 
            c.id as server_chat_id,
            c.user_1_id,
            c.user_2_id,
            c.last_message_text,
            c.last_message_time,
            u.username as other_username,
            u.first_name as other_first_name,
            u.last_name as other_last_name,
            u.profile_image_url as other_profile_image
        FROM chats c
        JOIN users u ON (
            CASE 
                WHEN c.user_1_id = ? THEN c.user_2_id = u.id 
                ELSE c.user_1_id = u.id 
            END
        )
        WHERE c.user_1_id = ? OR c.user_2_id = ?
        ORDER BY c.last_message_time DESC
    ";

    $stmt = $conn->prepare($sql);
    $stmt->bind_param("sss", $userId, $userId, $userId);
    $stmt->execute();
    $result = $stmt->get_result();

    $chats = [];
    while ($row = $result->fetch_assoc()) {
        // Normalize chat_id string for Android (smaller_larger)
        $u1 = $row['user_1_id'];
        $u2 = $row['user_2_id'];
        $localChatId = ($u1 < $u2) ? "{$u1}_{$u2}" : "{$u2}_{$u1}";

        $chats[] = [
            'localChatId' => $localChatId,
            'serverChatId' => $row['server_chat_id'],
            'otherUserId' => ($u1 == $userId) ? $u2 : $u1,
            'otherUserName' => $row['other_username'],
            'otherFullName' => trim($row['other_first_name'] . ' ' . $row['other_last_name']),
            'otherProfileImage' => $row['other_profile_image'],
            'lastMessage' => $row['last_message_text'],
            'lastMessageTime' => (int)$row['last_message_time']
        ];
    }

    echo json_encode($chats);
}
?>