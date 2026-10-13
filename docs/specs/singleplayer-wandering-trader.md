# Spec: comerciante aleatório para singleplayer

Status: pronta para implementação, ainda não implementada.
Data: 13/10/2026.
Projeto: TaleShop; Java 25; alvo atual Hytale Server 0.7.0.

## 1. Objetivo

Adicionar um NPC de encontro que aparece aleatoriamente perto do jogador, oferece trocas predefinidas e tem estoque limitado por troca e por aparição. O jogador deve poder comprar sem criar uma loja, colocar baús ou usar comandos administrativos.

Implementar como recurso opcional do TaleShop. O plugin continua funcionando no singleplayer e as lojas existentes continuam com seu comportamento atual. Não criar outro projeto ou outro JAR.

## 2. Escopo e decisões fechadas

- Um único perfil de comerciante nesta versão, usando o papel existente `Klops_Merchant`.
- NPC parado, com a mesma proteção usada pelos comerciantes existentes. Caminhada, novo modelo, animações e biomas específicos ficam para outra versão.
- Todas as ofertas válidas do catálogo aparecem em cada encontro, na ordem do JSON. Somente o surgimento e a posição são aleatórios; não sortear ofertas.
- Um comerciante ativo por mundo. Não tornar esse limite configurável nesta versão.
- Estoque virtual, sem baús. O pagamento é consumido e não fica em armazenamento físico.
- Cada oferta tem limite de compras; padrão 5, com sobrescrita por oferta.
- Sem reposição durante o encontro. Uma nova aparição começa com estoque novo.
- Comerciante esgotado permanece até o fim do tempo de permanência.
- Interagir sempre abre a compra, inclusive para proprietário do save ou administrador.
- Recurso desabilitado por padrão para preservar saves existentes. Ativar por configuração.
- Não tentar detectar se a sessão é singleplayer por APIs presumidas. Se ativado em servidor, aplicar as mesmas regras por mundo e escolher aleatoriamente um jogador elegível por tentativa.
- Configuração entra em vigor ao carregar o save/iniciar o servidor; não implementar hot reload.

## 3. Evidências do código atual

Os caminhos abaixo são relativos à raiz do repositório:

| Arquivo | Comportamento atual / relação com a mudança |
| --- | --- |
| `src/main/java/br/com/leonardson/taleshop/TaleShop.java` | Inicializa configuração, registry, interação e sistema de entidades. Deve integrar e encerrar o novo serviço. |
| `src/main/java/br/com/leonardson/taleshop/shop/TraderNpc.java` | Cria NPC a partir da posição de uma entidade, aplica interação, proteção e congelamento. Precisa permitir posição explícita sem mudar os comandos atuais. |
| `src/main/java/br/com/leonardson/taleshop/shop/TraderInteractableSystem.java` | Reaplica interação/congelamento ao carregar entidades reconhecidas pelo `ShopRegistry`. Deve também reconhecer os NPCs de encontro. |
| `src/main/java/br/com/leonardson/taleshop/interaction/TraderMessageInteraction.java` | Resolve loja pelo UUID e decide entre editor/compra. Resolver encontro primeiro e abrir sua compra diretamente. |
| `src/main/java/br/com/leonardson/taleshop/shop/ui/ShopBuyerPage.java` | UI e execução usam baús ou estoque infinito. Referência visual; não adaptar encontro usando `isAdmin = true`. |
| `src/main/java/br/com/leonardson/taleshop/shop/trade/Trade.java` | Define a receita, sem estoque. Não acrescentar estado de encontro a esse record. |
| `src/main/java/br/com/leonardson/taleshop/shop/Shop.java` | Representa lojas de jogadores/admin. Não representar encontro como loja de um proprietário fictício. |
| `src/main/java/br/com/leonardson/taleshop/config/PluginConfigManager.java` | Usa Gson e DTO com nomes capitalizados. Referência de convenção de configuração. |
| `src/test/java/br/com/leonardson/taleshop/` | Testes existentes de configuração, limite de ofertas e SQLite devem continuar passando. |

`MaxTradesPerShop` é limite de quantidade de ofertas de uma loja, não de compras. Não reutilizar essa configuração como estoque.

## 4. Configuração

Criar `WanderingTraderConfig.json` na pasta de dados do plugin, ao lado de `TaleShopConfig.json`. Usar manager próprio para não aumentar a complexidade nem alterar o carregamento da configuração existente.

Formato inicial obrigatório:

```json
{
  "Enabled": false,
  "NpcRole": "Klops_Merchant",
  "DisplayName": "Traveling Merchant",
  "AttemptIntervalSeconds": 600,
  "SpawnChance": 0.25,
  "MinSpawnDistance": 20,
  "MaxSpawnDistance": 40,
  "LifetimeSeconds": 600,
  "DefaultMaxUses": 5,
  "Trades": []
}
```

Estrutura de cada elemento de `Trades` (IDs ilustrativos; confirmar no registro de itens antes de publicar exemplos utilizáveis):

```json
{
  "Id": "iron_offer",
  "InputItemId": "ITEM_ID_VALIDO_DE_PAGAMENTO",
  "InputQuantity": 10,
  "OutputItemId": "ITEM_ID_VALIDO_DE_PRODUTO",
  "OutputQuantity": 2,
  "MaxUses": 3
}
```

Regras de validação:

- Campos opcionais ausentes recebem os defaults acima. `MaxUses` ausente usa `DefaultMaxUses`; zero não significa infinito.
- Chance finita entre 0 e 1; intervalos, duração e limites de compras inteiros positivos.
- Distâncias finitas, positivas e máxima maior ou igual à mínima.
- `Id` obrigatório, estável e único; não usar posição no array como identidade de oferta.
- Quantidades positivas e itens existentes no registro do jogo. Validar registro somente depois que os assets estiverem disponíveis.
- Papel do NPC existente; nome não vazio.
- JSON inválido ou parâmetros globais inválidos: suspender somente esse recurso e registrar erro útil. Não sobrescrever o arquivo inválido nem derrubar as lojas existentes.
- Oferta inválida: ignorar a oferta, registrar ID/motivo; duplicação de ID invalida o catálogo para evitar ambiguidade.
- Catálogo vazio/sem ofertas válidas: não gerar NPC e emitir aviso uma vez, sem spam a cada tick.
- Gerar configuração vazia/desabilitada é intencional. Documentar como obter IDs reais de itens; não inventar catálogo padrão de economia.

## 5. Organização sugerida

Criar pacote `br.com.leonardson.taleshop.wandering` com responsabilidades pequenas:

- `WanderingTraderConfig` e `WanderingTraderConfigManager`: DTO, defaults, parsing e validação.
- `WanderingTradeDefinition`: receita e limite configurados, imutáveis.
- `WanderingTraderEncounter`: ID do encontro, mundo, UUID do NPC, posição, snapshot das ofertas, usos restantes e duração restante.
- `WanderingTraderStateStorage`: ler/salvar estado JSON por mundo.
- `WanderingTraderService`: tentativas, ciclo de vida, lookup por UUID e reconciliação.
- `WanderingTraderSpawnResolver`: escolher/verificar posição usando API do mundo.
- `WanderingTraderTradeService`: validar/executar compras; lógica de estoque testável sem servidor rodando.
- `ui/WanderingTraderBuyerPage`: apresentação e eventos que delegam ao serviço de compra.

Os nomes podem mudar, mas manter configuração, estado, integração com o mundo e execução de compra separados. Não colocar toda a lógica em `TaleShop` ou na página.

## 6. Ciclo de vida e spawn

Usar tick/scheduler suportado pelo servidor 0.7.0. Toda leitura/mutação de entidades, blocos e inventários acontece na thread do mundo. Verificar assinaturas no JAR/dependência local antes de escrever integração; não inventar métodos nem adicionar cadeias de reflection como tentativa de compatibilidade.

Sem encontro ativo:

1. Contar tempo de simulação somente enquanto existir jogador elegível no mundo.
2. Primeira tentativa após `AttemptIntervalSeconds`; não gerar automaticamente ao entrar.
3. Ao vencer intervalo, sortear chance uma vez. `0` nunca gera; `1` sempre tenta posicionar.
4. Escolher jogador presente, com entidade válida e vivo. Em singleplayer será o próprio jogador.
5. Sortear até 16 candidatos no anel horizontal entre distância mínima e máxima. Para distribuição uniforme de área, raio = `sqrt(min² + u * (max² - min²))`.
6. Examinar somente chunks já carregados. Não forçar geração/carregamento para uma tentativa.
7. Validar chão com suporte, volume livre para o NPC, ausência de líquido/perigo conhecido e dentro dos limites válidos do mundo. Usar colisão/dimensões reais do papel ou API equivalente; não presumir que dois blocos vazios bastam.
8. Sem candidato seguro: desistir e aguardar intervalo completo novamente. Não usar a posição do jogador como fallback.
9. Criar snapshot do catálogo e contadores; gerar NPC; registrar UUID e salvar estado. Spawn/salvamento falhou: limpar NPC criado e impedir interação parcial.

Com encontro ativo:

- Não realizar novas tentativas.
- Reduzir duração apenas enquanto houver jogador elegível no mundo; save fechado não consome duração.
- Ao esgotar duração, invalidar compras primeiro, remover NPC e persistir fim do encontro.
- Reiniciar intervalo completo depois do fim. Não gerar substituto imediatamente.
- Sem jogadores, pausar duração e tentativas. Não multiplicar timers ao entrar/sair.
- Descarregamento de chunk não significa morte/desaparecimento e não libera slot para novo encontro.
- Se NPC for realmente removido/destruído, encerrar encontro e iniciar intervalo, sem reposição instantânea.
- Expiração com chunk descarregado: manter UUID marcado para remoção; remover quando carregar. Nunca reativar compras desse encontro.
- Desabilitar recurso: invalidar encontros existentes e limpar NPCs quando seus mundos/chunks estiverem disponíveis; não deixar NPC comercial órfão.
- No encerramento do plugin, cancelar trabalho agendado e salvar estado; não depender apenas de shutdown hook da JVM.

## 7. Estoque e compra

Estoque significa usos restantes por oferta, por encontro. Se a receita entrega 10 itens e tem 5 usos, podem sair 50 itens ao todo. Ofertas diferentes com o mesmo produto têm contadores independentes.

A página mostra `Remaining: 5 / 5`, reduz para `4 / 5` após compra e bloqueia oferta ao chegar a zero. A falta de pagamento também desabilita o botão. Não mostrar `INF` nem contar itens em baús.

Cada evento de compra deve conter `EncounterId` e `TradeId` estáveis. O servidor resolve preço/produto a partir do snapshot; nunca aceita quantidade/preço enviados pelo cliente.

Fluxo obrigatório, serializado na thread do mundo:

1. Validar encontro atual, ativo, mesmo mundo, NPC carregado/válido e duração positiva.
2. Validar distância horizontal/espacial de interação de até 8 blocos do NPC; página aberta não permite compras remotas.
3. Resolver oferta pelo ID e conferir estoque maior que zero.
4. Conferir itens de pagamento e capacidade de receber toda a saída, considerando os slots liberados pelo pagamento. Inventário cheio sem espaço suficiente: recusar antes de consumir pagamento.
5. Executar remoção e entrega usando os resultados reais das transações de inventário. Não assumir sucesso de uma chamada nem descartar resto no chão.
6. Consumir exatamente um uso somente após compra concluída; persistir e atualizar a página.
7. Se remoção/entrega/salvamento falhar, restaurar inventário e contador ao estado anterior. Não anunciar sucesso parcial. Se não for possível recuperar com segurança, bloquear novas compras desse encontro e registrar erro.

Planejar a transação com snapshot das alterações de slots e rollback, ou usar transação atômica suportada pelo jogo. Não reutilizar diretamente os helpers atuais que removem itens sem verificar retorno.

Eventos repetidos devem revalidar o estado atual; com apenas um uso restante, duas solicitações não podem produzir duas compras. UI antiga de encontro encerrado ou de oferta removida deve falhar sem efeitos e fechar/atualizar página.

Garantir consistência em erros controlados e em encerramento normal. Atomicidade entre arquivo JSON e inventário durante queda abrupta do processo não é promessa desta versão; registrar essa limitação, sem alegar garantia ACID.

## 8. Persistência e retomada

Salvar em `<pasta-de-dados>/wandering-traders/<id-estavel-do-mundo>.json`. Obter identidade estável do mundo pela API/configuração real; não usar apenas nome de exibição ou o UUID do jogador. Codificar/validar o identificador para formar caminho seguro.

Estado versionado (`SchemaVersion: 1`) deve incluir:

- Identidade do mundo e tempo restante até a próxima tentativa.
- Encontro ativo opcional: ID próprio, UUID do NPC, papel/nome, posição/rotação e duração restante.
- Snapshot de todas as receitas e seus limites iniciais; contadores restantes por ID.
- UUIDs com remoção pendente, inclusive depois de expiração/desativação.

Salvar após spawn, compra, encerramento de encontro e unload/shutdown; checkpoint de tempos no máximo a cada 30 segundos. Escrita por arquivo temporário e substituição atômica quando suportada. Erros não devem substituir estado válido por arquivo parcial.

Na retomada:

1. Carregar estado antes de habilitar compras/tentativas do mundo.
2. Reassociar entidade existente pelo UUID quando ela carregar; reaplicar interação e congelamento.
3. Não criar outro NPC só porque a entidade não foi encontrada em chunks carregados. A ausência em chunk descarregado não prova que ela deixou de existir.
4. Se for necessário recuperar NPC comprovadamente ausente, usar o mesmo encontro, receitas, duração e contadores; atualizar UUID sem restaurar estoque. Confirmar ausência via API/ciclo de carregamento do local persistido.
5. Se APIs não permitirem confirmar ausência, manter encontro suspenso até resolução/expiração; preferir suspensão a duplicação.
6. Configuração alterada afeta encontros futuros. Encontro existente usa snapshot persistido; itens/papel agora indisponíveis suspendem esse encontro com log, sem renovar estoque.
7. Arquivo corrompido/schema desconhecido: preservar arquivo e suspender recurso naquele mundo. Não apagar e gerar novo estoque silenciosamente.

## 9. Integração com lojas atuais

- Adicionar sobrecarga de spawn por posição/rotação em `TraderNpc`; o método atual deve delegar preservando comportamento das lojas.
- `TraderMessageInteraction` consulta serviço de encontros antes de `ShopRegistry`. Encontro reconhecido vai diretamente para nova página, sem teste de permissão administrativa.
- `TraderInteractableSystem` reconhece UUIDs dos encontros, inclusive pendências de remoção; manter caminho atual das lojas.
- Reaproveitar assets existentes de barter somente após verificar seletores e formato. Criar UI própria em `Common/UI/Custom/Pages/` se os assets nativos não permitirem mostrar usos claramente.
- Não alterar schema de `shops.json`/`shops.db`, contratos de `ShopStorage` ou comportamento de lojas de jogadores/admin para implementar esta versão.
- Estado dos encontros é JSON próprio mesmo quando `StorageBackend` das lojas é SQLite; documentar isso.
- Não adicionar comandos obrigatórios para jogar. Comando de debug é opcional e deve exigir permissão administrativa existente.

## 10. Sequência de implementação

Concluir e verificar cada etapa antes da próxima:

1. **Configuração e domínio:** parsing/defaults, validação estrutural, definições imutáveis, encontro e regras de usos. Testes unitários sem integração de spawn.
2. **Persistência:** JSON versionado por mundo, escrita segura, round-trip de snapshot/contadores/tempos e tratamento de corrupção.
3. **Compra e UI:** serviço de transação testável, página própria e eventos por ID. Validar estoque e falhas com adaptador de inventário falso.
4. **NPC e interação:** posição explícita, lookup por UUID, compra direta e reassociação. Preservar lojas existentes.
5. **Spawn e ciclo:** resolver posição, timers por mundo, expiração, unload/reload e encerramento do plugin.
6. **Documentação e verificação:** configuração de exemplo com IDs confirmados, README, CHANGELOG e roteiro manual.

Se integração exigir assinatura desconhecida, inspecionar a dependência alvo e exemplos locais primeiro. Reportar qualquer limitação concreta; não substituir requisito por stub, TODO ou método fictício.

## 11. Testes automatizados obrigatórios

- Config ausente gera defaults desabilitados; campo ausente usa default; JSON inválido é preservado.
- `MaxUses` por oferta sobrescreve padrão; zero/negativo, quantidades inválidas, chances não finitas e IDs duplicados são rejeitados.
- Cinco compras possíveis, sexta recusada; saída com quantidade maior que um consome só um uso.
- Ofertas com mesmo produto têm estoque independente.
- Falta de pagamento, falta de espaço, transação parcial, falha de persistência e encontro expirado não produzem compra nem reduzem estoque.
- Último uso não pode ser consumido duas vezes por eventos consecutivos.
- Evento antigo não compra em novo encontro; IDs resistem a reordenação do catálogo.
- Round-trip mantém receitas, contadores, tempos e UUIDs; mudança de configuração não modifica snapshot ativo.
- Dois mundos têm estados independentes; chunk unload não gera duplicata.
- Chance 0/1 e limite de tentativas de posição funcionam usando RNG/relógio/adaptadores injetáveis, sem sleeps reais.
- Checkpoints e reinício preservam intervalo/duração; serviço não deixa timers vivos após shutdown.
- Arquivo inválido suspende mundo sem alterar dados de lojas.

Executar `./gradlew build` com JDK 25 e alvo 0.7.0. Para JAR local: `./gradlew build -PhytaleServerJar=/caminho/absoluto/Server/HytaleServer.jar`. Manter os testes existentes, incluindo empacotamento SQLite. Não mudar a versão alvo para contornar falhas.

## 12. Critérios de aceite no jogo

Usar cópia de save, configuração habilitada, IDs válidos e intervalo/duração menores para validação:

- [ ] NPC surge em terreno seguro entre distâncias configuradas, sem comando nem baús.
- [ ] Proprietário do singleplayer com permissões administrativas vê compras ao interagir.
- [ ] Oferta de 5 usos permite exatamente 5 compras e aparece esgotada depois.
- [ ] Oferta de limite diferente respeita sobrescrita; saída múltipla mantém semântica por compra.
- [ ] Fechar/reabrir UI e sair/entrar no save não renovam estoque.
- [ ] Save fechado não consome duração; retomada não duplica NPC.
- [ ] Afastar-se com UI aberta impede compra; NPC expirado também impede compra.
- [ ] Inventário cheio recusa sem consumir pagamento/uso; testar slots liberados pelo pagamento.
- [ ] Descarregar/recarregar chunk restaura interação e estoque; expiração pendente remove NPC ao carregar.
- [ ] Próximo encontro começa somente após novo intervalo e recebe estoque inicial.
- [ ] Desativação limpa encontros e impede compras, inclusive após reload de chunk.
- [ ] Loja normal ainda usa baús; admin shop continua infinita; editor, comandos e ambos os backends das lojas funcionam.

Na entrega, informar arquivos alterados, resultado do build/testes e quais verificações foram efetivamente feitas no jogo. Não declarar testes manuais como concluídos apenas porque o build passou.
