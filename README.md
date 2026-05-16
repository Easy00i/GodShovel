# Easy Ai Java Full Version

This package includes:
- Spring Boot backend
- Separate admin page
- Login / register flow
- Coin shop
- Premium membership
- Chat-style builder UI
- Error popup with Fix Error button
- Jar build hook scaffold

## Run
1. Install Java 17+ and Maven
2. Go to `backend`
3. Run `mvn spring-boot:run`
4. Open `http://localhost:8080`
5. Open `http://localhost:8080/admin.html`

## Admin login
- Email: `admin@easyai.local`
- Password: `admin123`

## Notes
- Real Google/Discord OAuth and payment gateways need backend credentials.
- Build endpoint writes a temporary plugin project and tries `mvn package` on the server machine.
