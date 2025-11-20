<?php

/**
 * Generates a short-lived OAuth 2.0 access token using the Service Account JSON.
 * Requires OpenSSL extension enabled in php.ini.
 */
function get_access_token($service_account_path) {
    if (!file_exists($service_account_path)) {
        return null;
    }

    $credentials = json_decode(file_get_contents($service_account_path), true);
    
    // Create JWT Header
    $header = json_encode(['alg' => 'RS256', 'typ' => 'JWT']);
    
    // Create JWT Payload
    $now = time();
    $payload = json_encode([
        'iss' => $credentials['client_email'],
        'sub' => $credentials['client_email'],
        'aud' => 'https://oauth2.googleapis.com/token',
        'iat' => $now,
        'exp' => $now + 3600, // Token expires in 1 hour
        'scope' => 'https://www.googleapis.com/auth/firebase.messaging'
    ]);

    // Encode to Base64Url
    $base64UrlHeader = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($header));
    $base64UrlPayload = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($payload));

    // Sign the JWT
    $signature = '';
    openssl_sign($base64UrlHeader . "." . $base64UrlPayload, $signature, $credentials['private_key'], 'SHA256');
    $base64UrlSignature = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($signature));

    $jwt = $base64UrlHeader . "." . $base64UrlPayload . "." . $base64UrlSignature;

    // Exchange JWT for Access Token
    $ch = curl_init();
    curl_setopt($ch, CURLOPT_URL, 'https://oauth2.googleapis.com/token');
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_POSTFIELDS, http_build_query([
        'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        'assertion' => $jwt
    ]));
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false); 
    
    $response = json_decode(curl_exec($ch), true);
    curl_close($ch);

    return $response['access_token'] ?? null;
}

/**
 * Sends a notification using the FCM V1 API
 */
function send_fcm_notification($target_token, $title, $body, $data = []) {
    $service_account_path = __DIR__ . '/service_account.json';
    
    // 1. Get Project ID from JSON
    if (!file_exists($service_account_path)) {
        return json_encode(['error' => 'service_account.json not found']);
    }
    $json = json_decode(file_get_contents($service_account_path), true);
    $projectId = $json['project_id'];

    // 2. Get Access Token
    $accessToken = get_access_token($service_account_path);
    if (!$accessToken) {
        return json_encode(['error' => 'Could not generate access token']);
    }

    $url = "https://fcm.googleapis.com/v1/projects/$projectId/messages:send";

    // 3. Ensure data values are strings (Required by V1 API)
    $stringData = [];
    foreach ($data as $key => $val) {
        $stringData[$key] = (string)$val;
    }

    // 4. Construct Payload
    $message = [
        'message' => [
            'token' => $target_token,
            'notification' => [
                'title' => $title,
                'body' => $body
            ],
            'data' => $stringData
        ]
    ];

    // 5. Send Request
    $headers = [
        'Authorization: Bearer ' . $accessToken,
        'Content-Type: application/json'
    ];

    $ch = curl_init();
    curl_setopt($ch, CURLOPT_URL, $url);
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
    curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($message));

    $result = curl_exec($ch);
    curl_close($ch);

    return $result;
}
?>