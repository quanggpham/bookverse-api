# Assignment: BookVerse — E-Book Management System

XÂY DỰNG HỆ THỐNG BOOKVERSE

Yêu cầu chung: Hãy xây dựng một Web API + Web Application quản lý sách điện tử có tên
BookVerse. Hệ thống cho phép quản lý thông tin sách, upload và phục vụ ảnh bìa sách một cách
chuyên nghiệp.

## I. Yêu cầu chức năng

### 1. Quản lý sách (CRUD)
- Thêm, sửa, xóa, xem chi tiết sách.
- Các trường thông tin: id, title, author, isbn, year, category, rating, description, coverPath.

### 2. Upload và Quản lý Ảnh Bìa
- Hỗ trợ upload ảnh bìa (JPG, PNG, WebP).
- Khi upload phải tự động tạo 3 kích thước: thumbnail (200px), medium (500px), large (1200px).
- Lưu ảnh theo cấu trúc thư mục: `uploads/covers/yyyy/MM/id-size.webp`.

### 3. Tìm kiếm và Liệt kê
- API phân trang, lọc theo thể loại, năm xuất bản, sắp xếp theo tên / năm / rating.
- Tìm kiếm full-text theo tên sách và tác giả.

### 4. Các API chính (phải có)
| Method | Endpoint |
|--------|----------|
| GET | `/api/books?page=&size=&sort=` |
| GET | `/api/books/{id}` |
| POST | `/api/books` (hỗ trợ upload ảnh) |
| PUT | `/api/books/{id}` |
| DELETE | `/api/books/{id}` |
| GET | `/api/books/search?q=&category=` |
| GET | `/api/books/{id}/cover?size=large` (trả về file ảnh) |

## II. Yêu cầu kỹ thuật

- Sử dụng Spring Boot 3 (ưu tiên) hoặc Spark Java.
- Sử dụng Spring Data JPA + PostgreSQL (hoặc H2 cho môi trường dev).
- Áp dụng Layered Architecture (Controller, Service, Repository, DTO).
- Sử dụng MapStruct để mapping DTO ↔ Entity.
- Validation dữ liệu đầu vào.
- Caching cho ảnh và một số API thường dùng.
- Viết OpenAPI (Swagger) documentation.
- Code phải sạch, có comment rõ ràng, tuân thủ Java naming convention.

## III. Yêu cầu nâng cao

- Bulk upload sách từ file Excel/CSV + upload nhiều ảnh cùng lúc.
- Tự động chuyển đổi ảnh sang định dạng WebP.
- Dockerize ứng dụng (Dockerfile + docker-compose.yml).
- Viết Unit Test cho Service layer.
- Frontend đơn giản để tương tác với API.

---

**LƯU Ý:** Project của các bài tập upload lên git thì phải để public để review code
