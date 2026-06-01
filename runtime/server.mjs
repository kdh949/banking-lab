import { createLabHttpServer } from "./labApp.mjs";

const port = Number(process.env.PORT || 8080);
const server = await createLabHttpServer();

server.listen(port, () => {
  console.log(`Banking Lab runtime listening on http://127.0.0.1:${port}`);
});
