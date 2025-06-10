# Estrutura de Eleição

## Autores

| **Identificação** | **Nome** | **Formação** |
| :-: | :-: | :-: |
| <img src="https://github.com/dartmol203.png" width=100 height=100 alt="André Corrêa da Silva" class="img-thumbnail image"> | André Corrêa da Silva | Graduando em Engenharia de Software (UnB) |
| <img src="https://github.com/gabrielm2q.png" width=100 height=100 alt="Gabriel Mariano da Silva" class="img-thumbnail image"> | Gabriel Mariano da Silva | Graduando em Engenharia de Software (UnB) |

*Tabela 1: Identificação dos Autores*

## Descrição

O *building block* contido neste repositório tem por objetivo o desenvolvimento de um sistema de eleições, o qual descreve um processo da democracia, esse que visa a escolha de um representante dentre os candidatos possíveis, sendo escolhido aquele que receber a maior quantidade de votos.

Nele, é proposto o desenvolvimento de um sistema onde um agente solicita ao mediador a criação de uma eleição, o qual retorna ao solicitante um identificador para a eleição criada. O agente solicitante então compartilha o identificador recebido para os outros agentes votantes, os quais podem então se candidatar e apresentar suas propostas.

Atingido então o quórum mínimo de candidatos e participantes cadastrados na votação (respectivamente, 1 participante e 2 votantes, ao menos), o mediador se responsabiliza pela instanciação da urna com os candidatos. Destaca-se a possibilidade de configuração ao início da votação de pesos aos votos disponibilizados pelos agentes votantes (representando agentes com pesos diferentes na decisão a ser tomada).

Logo, dá-se então o início do processo eleitoral (informado pelo mediador aos participantes da votação). A mesma se estende por um período definido por um *timeout* iniciado pela urna. Após a coleta dos votos de todos os participantes ou o final do tempo disponibilizado, o mediador inicia o processo de contabilização do resultado, no qual é solicitado um balanço do resultado final à urna.

Após a contabilização do resultado pelo agente mediador, o mesmo informa o resultado da eleição a todos os votantes e então se responsabiliza pela deleção de todas as informações relativas à votação realizada, incluindo a urna dedicada à votação, encerrando assim o processo decisório.

### Projeto em Execução

<img src="" alt="Descrição do Print">

*Figura 1: Print do Projeto em Execução*

## Requisitos Técnicos

1. **Criação da Eleição:** mediante solicitação de um agente votante, o agente mediador deve ser capaz de criar uma eleição com um código de identificação para tal.
2. **Compartilhamento de Votação:** recebido o código de identificação da eleição, o agente votante que solicitou a eleição deve ser capaz de compartilhar o mesmo com os demais agentes votantes, os quais devem todos se registrarem no DF com o código recebido, afim de serem identificados como participantes da votação.
3. **Candidatura:** uma vez recebido o código de identificação da eleição, o agente votante deve ser capaz de, caso desejado, se candidatar à eleição, enviando uma proposta de candidatura ao mediador.
4. **Preparação da Urna:** recebidos os retornos dos agentes votantes e dos candidatos, o mediador deve ser capaz de instanciar uma urna - representada por um agente específico - com todos os candidatos aptos.
5. **Iniciação da Eleição:** instanciada a urna, o agente mediador deve ser capaz de informar o início da eleição aos agentes participantes da mesma.
6. **Fornecimento dos Votos:** os agentes votantes registrados no DF como participantes da eleição devem ser capazes de fornecerem seus votos para os candidatos registrados na urna. Caso o voto registrado não se encaixe em nenhuma das opções, ele deve ser considerado como voto nulo.
7. **Votação com Pesos:** o *building block* desenvolvido deve conceber a possibilidade de atribuição de pesos aos votos dos agentes de acordo com categorias pré-estabelecidas em cada votação.
8. **Finalização da Coleta dos Votos por *Timeout* ou Totalidade dos Votos Recebidos:** uma vez iniciada a votação, a urna deve iniciar, sob orientação do agente mediador, um *timeout* para o recebimento dos votos. Caso a urna receba todos os votos antes da finalização do *timeout*, ela deve ser capaz de informar ao agente mediador o feito. Por outro lado, finalizado o *timeout*, a urna não deve contabilizar nenhum possível voto recebido, ainda sendo capaz de informar ao agente mediador o ocorrido.
9. **Contabilização do Resultado:** finalizado o período de votação (por integralidade de participação do quórum de votantes ou por *timeout*), o agente mediador deve ser capaz de requisitar à urna um balanço dos votos recebidos, a qual deve ser capaz de retornar ao mesmo a informação desejada. Recebida a informação, o agente mediador deve ser capaz de contabilizar os resultados da votação.
10. **Informação do Resultado:**

## Requisitos para Execução

Para a efetiva execução do *building block* disposto no repositório, se faz necessária a instalação e configuração do *software* *Maven* em sua máquina. Para tal, basta seguir as instruções de instalação dispostas na [**documentação do *Maven***](https://maven.apache.org/install.html). Para o desenvolvimento do *building block*, foi utilizado o *Maven* na versão **3.8.7**. Além disso, todas as instruções de execução consideram o uso de sistemas operacionais baseados em *Linux*.

## Como Executar?

Para a execução do *building block*, é possível utilizar-se do *Makefile* adicionado ao repositório ao seguir os seguintes passos:

- Primeiramente, clone o repositório em sua máquina:

```bash
git clone https://github.com/SMA-building-blocks/{PREENCHER}.git
```

- Em seguida, vá para a pasta do repositório:

```bash
cd {PREENCHER}
```

- Para realizar a *build* do projeto e executá-lo em seguida, execute o seguinte comando:

```bash
make build-and-run
```

> 🚨 **IMPORTANTE:** Ao executar o projeto, primeiro será realizada a criação de todos os agentes participantes. Logo após, para a efetiva realização do propósito desejado pelo *building block*, é necessário pressionar **ENTER** no terminal para a continuidade da execução do código. Esta decisão foi tomada em prol de uma facilitação do uso do *sniffer* para a visualização da comunicação entre os agentes participantes.

- É possível realizar apenas a *build* do projeto com o seguinte comando:

```bash
make build
```

- Similarmente, é possível rodar o projeto após a geração de sua build com o seguinte comando:

```bash
make run
```

- É possível alterar a quantidade de agentes participantes ao passar a variável **QUORUM** seguida do número desejado, como pode ser visto abaixo (onde N representa o número desejado de agentes):

```bash
make build-and-run QUORUM=N"
```

- Por fim, para apagar os arquivos derivados da *build* do projeto, execute o seguinte comando:

```bash
make clean
```

- Para ter acesso a uma série de informações úteis para a execução do building block, basta executar o seguinte comando:

```bash
make help
```

## Referências

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Maecenas in bibendum diam. Vestibulum at sapien sit amet erat malesuada ultrices. Quisque faucibus purus dui. Sed egestas fringilla hendrerit. Nullam rutrum consectetur risus, dapibus tincidunt lorem pellentesque nec. Donec leo eros, euismod a gravida eu, faucibus eget leo. Quisque auctor, enim at hendrerit auctor, dui nulla dictum tortor, a convallis mauris ligula ut quam. Pellentesque dapibus enim libero, ut tristique dolor porta quis. Morbi eget sagittis nunc. Maecenas eget metus bibendum nulla feugiat vulputate. Vestibulum non accumsan eros, vel finibus arcu. Nunc vel convallis mauris.
