# 🚀 Projeto Fullstack

Este repositório contém uma aplicação fullstack dividida em duas partes:

* 🎨 **Frontend**: interface do usuário
* ⚙️ **Backend**: API e regras de negócio

---

## 📁 Estrutura do Projeto

```
📦 meu-repo
 ┣ 📁 frontend
 ┣ 📁 backend
 ┗ 📄 README.md
```

---

## 🖥️ Frontend

Localizado em `/frontend`

### Tecnologias utilizadas:

* React / Vite (ou a que você estiver usando)

### Como rodar:

```bash
cd frontend
npm install
npm run dev
```

A aplicação estará disponível em:

```
http://localhost:5173
```

---

## 🔧 Backend

Localizado em `/backend`

### Tecnologias utilizadas:

* Spring Boot (ou Node.js)

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

A API estará disponível em:

```
http://localhost:8080
```

---

## 🔗 Integração

O frontend se comunica com o backend via requisições HTTP (API REST).

Exemplo:

```
GET http://localhost:8080/api/usuarios
```

---

## ⚙️ Configurações

Recomenda-se o uso de variáveis de ambiente:

### Frontend (`.env`)

```
VITE_API_URL=http://localhost:8080
```

### Backend

Configurar porta e variáveis no `application.properties` ou `.env`

---

## 📌 Observações

* Cada parte do projeto é independente
* Não alterar arquivos diretamente fora de suas respectivas pastas
* Certifique-se de que o backend esteja rodando antes do frontend

---

## 👨‍💻 Autor

Desenvolvido por Luan Hirai
