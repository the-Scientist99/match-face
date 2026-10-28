CREATE DATABASE IF NOT EXISTS sportsapp CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS sportsapp_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'sportsapp'@'localhost' IDENTIFIED BY 'sportsapp';
GRANT ALL PRIVILEGES ON sportsapp.* TO 'sportsapp'@'localhost';
GRANT ALL PRIVILEGES ON sportsapp_test.* TO 'sportsapp'@'localhost';
