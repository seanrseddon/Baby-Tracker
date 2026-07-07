FROM node:18-alpine

# Create app directory
WORKDIR /app

# Copy dependency manifests
COPY package.json ./

# Install dependencies (sqlite3 requires build tools on some platforms, pre-built binaries are used on Alpine)
RUN npm install --omit=dev

# Copy app source code
COPY server.js ./
COPY public/ ./public/

# Set environment variables
ENV PORT=3000
ENV DATA_DIR=/data
ENV NODE_ENV=production

# Create volume mount point for local database persistence (Unraid data folder)
RUN mkdir -p /data
VOLUME /data

# Expose port
EXPOSE 3000

# Start server
CMD ["node", "server.js"]
