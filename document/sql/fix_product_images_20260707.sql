SET NAMES utf8mb4;
START TRANSACTION;
CREATE TABLE IF NOT EXISTS pms_product_image_backup_20260707 (
  id BIGINT PRIMARY KEY,
  old_pic VARCHAR(500),
  old_album_pics VARCHAR(1000),
  backup_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO pms_product_image_backup_20260707 (id, old_pic, old_album_pics, backup_time)
SELECT id, pic, album_pics, NOW() FROM pms_product WHERE id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 18, 22, 23, 24, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45);
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-1.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-1.png' WHERE id = 1;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-2.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-2.png' WHERE id = 2;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-3.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-3.png' WHERE id = 3;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-4.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-4.png' WHERE id = 4;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-5.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-5.png' WHERE id = 5;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-6.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-6.png' WHERE id = 6;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-7.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-7.png' WHERE id = 7;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-8.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-8.png' WHERE id = 8;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-9.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-9.png' WHERE id = 9;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-10.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-10.png' WHERE id = 10;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-11.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-11.png' WHERE id = 11;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-12.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-12.png' WHERE id = 12;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-13.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-13.png' WHERE id = 13;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-14.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-14.png' WHERE id = 14;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-18.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-18.png' WHERE id = 18;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-22.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-22.png' WHERE id = 22;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-23.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-23.png' WHERE id = 23;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-24.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-24.png' WHERE id = 24;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-26.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-26.png' WHERE id = 26;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-27.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-27.png' WHERE id = 27;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-28.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-28.png' WHERE id = 28;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-29.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-29.png' WHERE id = 29;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-30.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-30.png' WHERE id = 30;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-31.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-31.png' WHERE id = 31;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-32.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-32.png' WHERE id = 32;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-33.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-33.png' WHERE id = 33;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-34.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-34.png' WHERE id = 34;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-35.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-35.png' WHERE id = 35;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-36.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-36.png' WHERE id = 36;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-37.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-37.png' WHERE id = 37;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-38.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-38.png' WHERE id = 38;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-39.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-39.png' WHERE id = 39;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-40.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-40.png' WHERE id = 40;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-41.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-41.png' WHERE id = 41;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-42.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-42.png' WHERE id = 42;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-43.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-43.png' WHERE id = 43;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-44.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-44.png' WHERE id = 44;
UPDATE pms_product SET pic = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-45.png', album_pics = 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-45.png' WHERE id = 45;
COMMIT;
SELECT COUNT(*) AS fixed_product_count FROM pms_product WHERE id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 18, 22, 23, 24, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45) AND pic LIKE 'http://114.55.170.17:8201/minio/mall/20260707/fix/product-%.png';
