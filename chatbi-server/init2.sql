-- 初始化数据库脚本
-- 这个文件会在MySQL容器启动时自动执行

CREATE DATABASE IF NOT EXISTS test2_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE test2_db;

-- 创建用户表
CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID，主键',
    name VARCHAR(100) NOT NULL COMMENT '用户姓名',
    email VARCHAR(100) UNIQUE NOT NULL COMMENT '邮箱，唯一',
    age INT COMMENT '年龄',
    city VARCHAR(50) COMMENT '所在城市',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='用户信息表';

-- 创建订单表
CREATE TABLE IF NOT EXISTS orders (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID，主键',
    user_id INT NOT NULL COMMENT '下单用户ID，外键 users.id',
    product_name VARCHAR(200) NOT NULL COMMENT '商品名称',
    amount DECIMAL(10,2) NOT NULL COMMENT '订单金额',
    quantity INT NOT NULL COMMENT '数量',
    order_date DATE NOT NULL COMMENT '下单日期',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '订单状态',
    FOREIGN KEY (user_id) REFERENCES users(id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='订单明细表';

-- 创建商品表
CREATE TABLE IF NOT EXISTS products (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '商品ID，主键',
    name VARCHAR(200) NOT NULL COMMENT '商品名称',
    category VARCHAR(100) NOT NULL COMMENT '商品分类',
    price DECIMAL(10,2) NOT NULL COMMENT '单价',
    stock INT NOT NULL COMMENT '库存数量',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='商品信息表';

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
('iPhone 15', '手机', 7998.00, 100),
('MacBook Pro', '电脑', 15998.00, 50),
('iPad Air', '平板', 4398.00, 80),
('AirPods Pro', '耳机', 1898.00, 200),
('Apple Watch', '手表', 2998.00, 150),
('Samsung Galaxy', '手机', 5998.00, 120),
('Dell XPS', '电脑', 8998.00, 60),
('Sony WH-1000XM4', '耳机', 2198.00, 90);

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

-- 创建数据库连接表
CREATE TABLE IF NOT EXISTS database_connection (
    id VARCHAR(255) PRIMARY KEY COMMENT '连接ID，主键',
    name VARCHAR(255) NOT NULL COMMENT '连接名称',
    host VARCHAR(255) NOT NULL COMMENT '数据库主机地址',
    port INT NOT NULL DEFAULT 3306 COMMENT '数据库端口',
    username VARCHAR(255) NOT NULL COMMENT '数据库用户名',
    password VARCHAR(255) NOT NULL COMMENT '数据库密码',
    database VARCHAR(255) NOT NULL COMMENT '数据库名称',
    charset VARCHAR(50) DEFAULT 'utf8mb4' COMMENT '字符集',
    description TEXT COMMENT '连接描述',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否激活',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='数据库连接配置表';

-- 插入默认数据库连接配置
INSERT INTO database_connection (id, name, host, port, username, password, database, charset, description, is_active, created_at, updated_at) VALUES
('0e21075e-f8c9-493d-85c2-bba71aba0afc', '默认数据库2', 'mysql', 3306, 'root', 'password', 'test2_db', 'utf8mb4', '', TRUE, '2025-09-22 17:25:32', '2025-09-22 17:25:32');
