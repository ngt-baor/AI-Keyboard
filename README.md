# Gõ Tự Nhiên

Bàn phím Android có chế độ dịch bản nháp tiếng Việt sang tiếng Anh hoặc tiếng Nga. Bản đầu tập trung vào gõ Telex, dịch cục bộ và cho xem trước trước khi thay nội dung trong ô chat.

## Cài APK để thử

Repo chứa mã nguồn; APK không được đính kèm. Tạo APK debug bằng lệnh sau rồi chép `app/build/outputs/apk/debug/app-debug.apk` sang điện thoại:

```powershell
.\gradlew.bat :app:assembleDebug
```

1. Nếu Android hỏi, cho phép ứng dụng Tệp hoặc trình duyệt cài ứng dụng chưa rõ nguồn.
2. Mở **Gõ Tự Nhiên**, vào cài đặt để bật bàn phím, rồi chọn **Gõ Tự Nhiên** làm bàn phím hiện tại.
3. Bấm tải mô hình dịch khoảng **329 MiB**. Tải qua HTTPS từ [LiteRT Community trên Hugging Face](https://huggingface.co/litert-community/Qwen3-0.6B); ứng dụng kiểm tra kích thước và SHA-256 trước khi dùng.
4. Trong ứng dụng chat, nhập tiếng Việt bằng Telex. Bấm **Dịch**, chọn **English** hoặc **Русский**, rồi **Dịch bản nháp**.
5. Xem lại kết quả và bấm **Dùng** để thay bản nháp. Ứng dụng không tự gửi tin; bạn gửi từ ứng dụng chat như bình thường.

Để gõ tiếng Anh bình thường, chuyển nút **VI** sang **EN**. Có thể bấm nút bàn phím ở hàng trên để mở bộ chọn bàn phím Android.

## Quyền riêng tư và giới hạn bản thử

- Ứng dụng không có máy chủ dịch hay tài khoản. Phần dịch gọi mô hình LiteRT-LM ngay trên điện thoại; nội dung tin nhắn không được đưa vào yêu cầu tải mô hình.
- Quyền mạng chỉ phục vụ tải tệp mô hình lần đầu. Sau khi tải xong, dịch không cần mạng.
- Tin nhắn và bản dịch chỉ được giữ tạm trong bộ nhớ khi dịch. Không ghi chúng vào log hoặc lưu trong ứng dụng.
- Trường mật khẩu, số và trường không phải văn bản thông thường không cho phép dịch.
- Chỉ thay bản nháp nếu ứng dụng chat cung cấp toàn bộ nội dung cho bàn phím và nội dung chưa đổi kể từ khi tạo bản dịch. Nếu không, bản gốc được giữ nguyên.
- APK này ký bằng chứng thư debug để cài thử. Chưa phải bản phát hành cửa hàng ứng dụng.
- Chất lượng dịch, mức dùng bộ nhớ, nhiệt độ và tốc độ chưa được đo trên thiết bị thật. Lần khởi tạo mô hình đầu tiên có thể chậm; cần đánh giá thực tế trước khi chốt model.

## Build từ mã nguồn

Yêu cầu JDK 17 trở lên và Android SDK. Đặt `ANDROID_HOME` và `ANDROID_SDK_ROOT` tới thư mục SDK trên máy của bạn, hoặc cấu hình `sdk.dir` trong `local.properties` (file này không được đưa lên Git). Project ghim Android Gradle Plugin 8.13.2, Gradle 8.13, Kotlin 2.3.21 và LiteRT-LM 0.17.1. Kiểm tra phiên bản ổn định mới nhất trên nguồn chính thức trước khi cập nhật dependency.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

APK được tạo tại `app/build/outputs/apk/debug/app-debug.apk`.

## Tài liệu kỹ thuật

- [Android Input Method Service](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method)
- [LiteRT-LM Android Kotlin API](https://developers.google.com/edge/litert-lm/android)
- [LiteRT-LM model support and benchmarks](https://developers.google.com/edge/litert-lm/overview)
