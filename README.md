# Biblioteca Digital — gRPC com Java

**Disciplina:** Computação Distribuída — Universidade Presbiteriana Mackenzie
**Aluno:** Gabriel Tortolio Fonseca — **RA:** 10416751

## Descrição

Backend de um **Sistema de Gerenciamento de Biblioteca Digital** distribuído, feito com
**Java 17 + Maven + gRPC 1.68.1 + Protocol Buffers 3**. Um servidor gRPC central expõe o
`BibliotecaService`, com um RPC de cada tipo:

| RPC | Tipo | O que faz |
|---|---|---|
| `cadastrarLivro` | Unary | Cadastra um livro e devolve ID + status |
| `listarLivrosPorAutor` | Server streaming | Devolve, um a um, os livros de um autor |
| `registrarEmprestimos` | Client streaming | Recebe vários empréstimos e responde com um resumo (total e tempo) |
| `chatBibliotecario` | Bidirecional | Para cada mensagem do usuário, sugere um livro pela palavra-chave |

Os dados ficam em memória (`HashMap` / `List`), na classe `Acervo`.

### Tratamento de erros (`io.grpc.Status`)

| Situação | Status |
|---|---|
| Campos obrigatórios vazios / ano inválido / mensagem de chat vazia | `INVALID_ARGUMENT` |
| ISBN já cadastrado | `ALREADY_EXISTS` |
| Autor sem livros cadastrados | `NOT_FOUND` |
| Token ausente ou inválido | `UNAUTHENTICATED` |

No client streaming, empréstimos com usuário vazio ou `livro_id` inexistente são contados como *rejeitados* no resumo (o restante do stream continua sendo processado).

### Bônus implementados

- **Interceptor de log** (`LogInterceptor`): registra método, tipo, status final e duração de toda chamada.
- **Deadline**: o cliente aplica `withDeadlineAfter(...)` em todas as chamadas.
- **Autenticação por metadata** (`AuthInterceptor`): exige o header `authorization: Bearer biblioteca-secret-123`; o cliente o anexa via `MetadataUtils.newAttachHeadersInterceptor`.

## Estrutura

```text
biblioteca-grpc/
├── pom.xml
└── src/main
    ├── proto/biblioteca.proto
    └── java/br/mackenzie/biblioteca
        ├── server/  BibliotecaServiceImpl, ServidorBiblioteca, LogInterceptor, AuthInterceptor, Log
        ├── client/  ClienteBiblioteca
        └── model/   Acervo, LivroModel, EmprestimoModel, TextoUtil, Seguranca
```

## Como compilar e executar

Pré-requisitos: Java 17+ e Maven 3.8+.

```bash
mvn clean compile        # gera as classes do gRPC a partir do .proto e compila
mvn clean package        # compila e empacota
```

**Terminal 1 — servidor** (porta 50051):

```bash
mvn exec:java -Dexec.mainClass=br.mackenzie.biblioteca.server.ServidorBiblioteca
```

**Terminal 2 — cliente** (executa todo o roteiro de testes):

```bash
mvn exec:java -Dexec.mainClass=br.mackenzie.biblioteca.client.ClienteBiblioteca
```

## Roteiro de testes demonstrado no cliente

1. Cadastro de 3 livros (Unary)
2. Listagem dos livros de um autor cadastrado (Server streaming)
3. Listagem de autor inexistente → `NOT_FOUND`
4. Registro de 5 empréstimos (Client streaming)
5. Chat com 4 mensagens (Bidirecional)
6. Cadastro com ISBN duplicado → `ALREADY_EXISTS`
7. Extras: `INVALID_ARGUMENT` (título vazio) e `UNAUTHENTICATED` (sem token)


## Prints da execução

![alt text](image.png)]
![alt text](image-1.png)
![alt text](image-2.png)