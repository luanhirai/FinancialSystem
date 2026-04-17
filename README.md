# 🚀 Projeto Fullstack

Este repositório contém uma aplicação fullstack dividida em duas partes:

* 🎨 **Frontend**: interface do usuário / user interface
* ⚙️ **Backend**: API e regras de negócio / API and business rules

---

## 📁 Estrutura do Projeto / project estructure 

```
📦 my repo
 ┣ 📁 frontend
 ┣ 📁 backend
 ┗ 📄 README.md
```

---

## 🖥️ Frontend

Localizado em `/frontend` / located in 'frontend'

### Tecnologias utilizadas: / technologies used:

* React / Next

### Como rodar: / how to start:

```bash
cd frontend
npm install
npm run dev
```

A aplicação estará disponível em: / The application stay available in:

```
http://localhost:5173
```

---

## 🔧 Backend

Localizado em `/backend` / located in:

### Tecnologias utilizadas: / technoligies used:

* Spring Boot with hibernate

### Como rodar:

#### Spring Boot:

```bash
cd backend
./mvnw spring-boot:run
```

#### Node.js:

```bash
cd backend
npm install
npm run dev
```

A API estará disponível em: / The API will be available in: 

```
http://localhost:8080
```

---

## 🔗 Integração / integration

O frontend se comunica com o backend via requisições HTTP (API REST).
/
The frontend comunicates with the backend via HTTP requests (REST API).

Exemplo:

```
GET http://localhost:8080/api/usuarios
```

---

## ⚙️ Configurações / settings:

Recomenda-se o uso de variáveis de ambiente: / The use of environment variables is recommended:

### Frontend (`.env`)

```
VITE_API_URL=http://localhost:8080
```

### Backend

Configurar porta e variáveis no `application.properties` ou `.env`
Configure the port and variables in `application.properties` or `.env`
---

## 📌 Observações / Observations

* Cada parte do projeto é independente / each project is independent
* Não alterar arquivos diretamente fora de suas respectivas pastas
  /
* Do not modify file directly outside of their respective folders.
* Certifique-se de que o backend esteja rodando antes do frontend

---

## 👨‍💻 Autor / Author

Desenvolvido por Luan Hirai / Developed by Luan Hirai
