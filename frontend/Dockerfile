FROM node:24-alpine AS build
WORKDIR /app
ENV HUSKY=0

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm install --no-audit --no-fund

COPY . .
RUN [ -f .env.local ] || cp .env.example .env.local
RUN npm run build

FROM nginx:1.30-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
