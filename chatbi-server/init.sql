-- 初始化数据库脚本
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
('吴十', 'wushi@example.com', 27, '西安');

-- 插入示例商品数据
INSERT INTO products (name, category, price, stock) VALUES
('iPhone 15', '手机', 7999.00, 100),
('MacBook Pro', '电脑', 15999.00, 50),
('iPad Air', '平板', 4399.00, 80),
('AirPods Pro', '耳机', 1899.00, 200),
('Apple Watch', '手表', 2999.00, 150),
('Samsung Galaxy', '手机', 5999.00, 120),
('Dell XPS', '电脑', 8999.00, 60),
('Sony WH-1000XM4', '耳机', 2199.00, 90);

-- 插入示例订单数据
INSERT INTO orders (user_id, product_name, amount, quantity, order_date, status) VALUES
(1, 'iPhone 15', 7999.00, 1, '2024-01-15', 'completed'),
(2, 'MacBook Pro', 15999.00, 1, '2024-01-16', 'completed'),
(1, 'AirPods Pro', 1899.00, 2, '2024-01-17', 'pending'),
(3, 'iPad Air', 4399.00, 1, '2024-01-18', 'completed'),
(4, 'Apple Watch', 2999.00, 1, '2024-01-19', 'completed'),
(5, 'Samsung Galaxy', 5999.00, 1, '2024-01-20', 'pending'),
(2, 'Dell XPS', 8999.00, 1, '2024-01-21', 'completed'),
(6, 'Sony WH-1000XM4', 2199.00, 1, '2024-01-22', 'completed'),
(7, 'iPhone 15', 7999.00, 1, '2024-01-23', 'completed'),
(8, 'MacBook Pro', 15999.00, 1, '2024-01-24', 'pending'),
(3, 'iPad Air', 4399.00, 1, '2024-01-25', 'completed'),
(4, 'AirPods Pro', 1899.00, 2, '2024-01-26', 'completed'),
(5, 'Apple Watch', 2999.00, 1, '2024-01-27', 'pending'),
(6, 'Samsung Galaxy', 5999.00, 1, '2024-01-28', 'completed'),
(7, 'Dell XPS', 8999.00, 1, '2024-01-29', 'completed'),
(8, 'Sony WH-1000XM4', 2199.00, 1, '2024-01-30', 'completed');
