# Jâmes v4.1 — arquitetura e limites operacionais

## Identidade
Jâmes é o assistente pessoal de Jamerson Ramos. Esta versão organiza a experiência em três áreas principais, mantendo o Worker e o banco existentes para não descartar conversas ou memórias.

## As três áreas
1. **Central holográfica** — esfera neural holográfica 3D animada com malha de nós e conexões, controle principal ON/OFF, voz ao vivo e câmera somente após ativação e concessão de permissões.
2. **Chat** — conversa por texto, histórico do servidor para a sessão selecionada, anexos aceitos pelo Worker e criação de múltiplas conversas locais. A lista de conversas é guardada no navegador deste aparelho; ainda não é uma conta sincronizada entre dispositivos.
3. **Painel de controle** — voz e perfis, percepção/anexos, diagnóstico do dispositivo, tarefas, memórias e integração Windows existente.

## Núcleo preservado
- Cloudflare Worker `james-ai` e binding D1 `DB`.
- Histórico `james_conversation_messages_v21`.
- Tarefas `james_tasks_v21` e memórias explícitas `james_memories_v21`.
- Ferramentas permitidas no Worker, chamadas de voz ao vivo via token temporário e ponte Android quando o app nativo estiver instalado.
- PWA instalável pelo navegador, com cache versionado.

## O que ON/OFF realmente faz
O botão inicia/encerra uma sessão ao vivo e solicita microfone/câmera conforme o modo escolhido. O clique não concede todas as permissões automaticamente: Android e navegador mostram seus próprios diálogos e o usuário precisa aprovar cada permissão. A câmera e o microfone não são ativados silenciosamente.

## Limites que não podem ser removidos por código de interface
- Uma página PWA não pode manter microfone/câmera ligados 24 horas em segundo plano nem contornar as restrições do Android.
- O Android exige permissões específicas e impõe restrições a serviços de câmera/microfone em segundo plano, sobretudo a partir do Android 12/14. Para operação de voz em segundo plano será necessário um aplicativo Android nativo com serviço em primeiro plano e declaração de tipos/permissões adequados, iniciado enquanto o app está visível.
- A interface holográfica é uma visualização gráfica, não um projetor físico.
- O modelo de IA não é uma rede neural treinada pelo Jâmes nem aprende pesos continuamente com as conversas. O produto oferece contexto e memórias explícitas no D1.
- Não existe acesso irrestrito a todos os aplicativos, câmeras, sites ou aparelhos. Integrações exigem APIs, conectores, instalação e permissões compatíveis.
- A versão atual não replica integralmente todos os recursos do ChatGPT, nem oferece armazenamento ilimitado, sincronização universal ou ferramentas sem limites.

## Próximas etapas técnicas
- Validar os três modos no navegador Android e em telas maiores.
- Executar testes de integração reais do Worker/Live API e confirmar o estado das migrações D1 antes de produção.
- Desenvolver/validar o app Android nativo (APK) com serviço de voz em primeiro plano, controles persistentes, notificações e ponte de ações; nunca alegar 24/7 sem teste em dispositivo.
- Galeria de referências visuais implementada via API pública do Wikimedia Commons; resultados exibidos com título/licença/crédito e rotação de 7 segundos. A pesquisa usa palavras-chave extraídas localmente do assunto; disponibilidade depende da API pública e da conexão.
- Adicionar sincronização autenticada de conversas entre dispositivos e controles de exportação/apagamento.

## Custo e privacidade
A interface e o código são preparados para recursos gratuitos quando possível. Uso da API Gemini, armazenamento, rede e downloads podem ter limites ou cobrança conforme plano e política do provedor. Não ativar recursos pagos sem consentimento. Dados de microfone/câmera são enviados somente durante sessões explicitamente iniciadas e autorizadas.
