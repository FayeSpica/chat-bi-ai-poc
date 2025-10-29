-- 示例数据库初始化脚本（test_db）
-- 这个文件会在MySQL容器启动时自动执行

CREATE DATABASE IF NOT EXISTS test_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE test_db;

-- 创建用户表
CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    age INT,
    city VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建订单表
CREATE TABLE IF NOT EXISTS orders (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    quantity INT NOT NULL,
    order_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    FOREIGN KEY (user_id) REFERENCES users(id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建商品表
CREATE TABLE IF NOT EXISTS products (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(100) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 插入示例用户数据
INSERT INTO users (name, email, age, city) VALUES
('张三', 'zhangsan@example.com', 25, '北京'),
('李四', 'lisi@example.com', 30, '上海'),
('王五', 'wangwu@example.com', 28, '广州'),
('赵六', 'zhaoliu@example.com', 35, '深圳'),
('钱七', 'qianqi@example.com', 22, '杭州'),
('孙八', 'sunba@example.com', 29, '成都'),
('周九', 'zhoujiu@example.com', 31, '武汉'),
('吴十', 'wushi@example.com', 27, '西安')
ON DUPLICATE KEY UPDATE name=name;

-- 插入示例商品数据
INSERT INTO products (name, category, price, stock) VALUES
('iPhone 15', '手机', 7999.00, 100),
('MacBook Pro', '电脑', 15999.00, 50),
('iPad Air', '平板', 4799.00, 80),
('AirPods Pro', '耳机', 1899.00, 200),
('Apple Watch', '手表', 2999.00, 150),
('Magic Mouse', '鼠标', 799.00, 100),
('Magic Keyboard', '键盘', 1299.00, 80),
('Studio Display', '显示器', 11499.00, 30)
ON DUPLICATE KEY UPDATE name=name;

-- 插入示例订单数据
INSERT INTO orders (user_id, product_name, amount, quantity, order_date, status) VALUES
(1, 'iPhone 15', 7999.00, 1, '2024-01-15', 'completed'),
(1, 'AirPods Pro', 1899.00, 2, '2024-01-20', 'completed'),
(2, 'MacBook Pro', 15999.00, 1, '2024-02-01', 'completed'),
(2, 'Magic Mouse', 799.00, 1, '2024-02-05', 'pending'),
(3, 'iPad Air', 4799.00, 1, '2024-02-10', 'completed'),
(3, 'Apple Watch', 2999.00, 1, '2024-02-12', 'pending'),
(4, 'Magic Keyboard', 1299.00, 2, '2024-02-15', 'completed'),
(4, 'Studio Display', 11499.00, 1, '2024-02-20', 'pending'),
(5, 'iPhone 15', 7999.00, 1, '2024-03-01', 'completed'),
(5, 'AirPods Pro', 1899.00, 1, '2024-03-05', 'completed'),
(6, 'iPad Air', 4799.00, 2, '2024-03-10', 'pending'),
(6, 'Apple Watch', 2999.00, 1, '2024-03-12', 'completed'),
(7, 'MacBook Pro', 15999.00, 1, '2024-03-15', 'pending'),
(7, 'Magic Mouse', 799.00, 3, '2024-03-18', 'completed'),
(8, 'Magic Keyboard', 1299.00, 1, '2024-03-20', 'completed'),
(8, 'Studio Display', 11499.00, 1, '2024-03-25', 'pending')
ON DUPLICATE KEY UPDATE user_id=user_id;
