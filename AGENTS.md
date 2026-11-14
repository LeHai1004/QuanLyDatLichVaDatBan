# BookTable: luật cho AI agent

App đặt bàn nhà hàng, Kotlin Android (XML + ViewBinding), Firebase. Package: com.ute.quanlydatban.

## Cách làm một thẻ Trello
1. Đọc mô tả thẻ và dòng "Figma:" trong thẻ. Dùng Figma MCP lấy đúng frame đó, dựng UI giống y đúc (màu, chữ, khoảng cách, bo góc, icon).
2. Tên frame Figma = tên màn hình trong code (BookingConfirmActivity -> BookingConfirmActivity.kt + activity_booking_confirm.xml).
3. Chỉ làm đúng phạm vi thẻ. Mục "KHÔNG LÀM" trong thẻ là bắt buộc.
4. Không dùng Playwright. Không mở trình duyệt để test.

## Giao diện
- Màu cam #E8622C, nền kem #FFF8F2, font Be Vietnam Pro.
- Không gõ chữ/màu/kích thước cứng trong layout hoặc code: dùng strings.xml, colors.xml, dimens.xml. Nếu Figma có màu mới, thêm vào colors.xml (chỉ thêm cuối file).
- Id view có tiền tố theo loại: tvTitle, btnApply, etCode, rvVouchers.
- Layout: activity_xxx.xml, fragment_xxx.xml, item_xxx.xml.
- Mọi màn luôn xử lý 3 trạng thái: đang tải, rỗng, lỗi.

## Code
- Kiến trúc: UI -> ViewModel -> Repository -> Firestore. UI không gọi Firestore trực tiếp.
- Thư mục: ui/customer, ui/owner, data/model, data/repository, viewmodel, util. Mỗi tính năng một gói con.
- Lớp PascalCase, hàm/biến camelCase, hằng UPPER_SNAKE_CASE.
- Tiền: kiểu Long (đồng), hiển thị bằng formatMoney(). Không dùng Double.
- Trạng thái đơn/món chỉ đổi qua một hàm trong Repository, không gán trong UI.
- Mọi thao tác Firestore có thành công/thất bại; lỗi hiện Snackbar tiếng Việt, không để app văng.
- Comment ngắn, giải thích vì sao; không để code chết bị comment.

## File dùng chung
themes.xml, colors.xml, strings.xml, AndroidManifest.xml, nav_graph.xml: chỉ thêm vào cuối file hoặc thêm khối riêng. Không sắp xếp lại, không format cả file. Không sửa Repository/file của người khác.

## Git
- Không push thẳng main/develop. Nhánh: feature/<ten>-<viec>, fix/<ten>-<loi>, docs/<viec>.
- Commit nhỏ, một việc, dạng: feat(scope): mô tả ngắn (feat, fix, refactor, style, docs, chore, test).
- Không git push --force. Không dùng Squash and merge.
- Không commit: google-services.json, *.keystore, local.properties, .idea/, build/, khóa API. Repo này PUBLIC.