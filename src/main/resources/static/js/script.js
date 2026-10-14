// Alternar sidebar em telas pequenas
document.querySelector('.toggle-btn').addEventListener('click', () => {
    document.getElementById('sidebar').classList.toggle('active');
});

// Alternar submenu
function toggleSubmenu(event, element) {
    event.preventDefault();
    element.parentElement.classList.toggle('open');
}

const Mensagem = {
    mostrar(texto, tipo = 'success') {
        const el = document.getElementById('toastMensagem');
        el.className = 'toast align-items-center border-0 text-bg-' + tipo;
        el.querySelector('.toast-body').textContent = texto;
        bootstrap.Toast.getOrCreateInstance(el, { delay: 4000 }).show();
    },

    aposRecarregar(texto, tipo = 'success') {
        sessionStorage.setItem('mensagemPendente', JSON.stringify({ texto, tipo }));
    }
};

document.addEventListener('DOMContentLoaded', () => {
    const pendente = sessionStorage.getItem('mensagemPendente');
    if (pendente) {
        sessionStorage.removeItem('mensagemPendente');
        const { texto, tipo } = JSON.parse(pendente);
        Mensagem.mostrar(texto, tipo);
    }
});

const Dialogo = {
    abrir(opcoes) {
        const el = document.getElementById('dialogoModal');
        const modal = bootstrap.Modal.getOrCreateInstance(el);
        const mensagem = el.querySelector('#dialogoMensagem');
        const grupo = el.querySelector('#dialogoCampoGrupo');
        const input = el.querySelector('#dialogoInput');
        const texto = el.querySelector('#dialogoTexto');
        const erro = el.querySelector('#dialogoErro');
        const botao = el.querySelector('#dialogoConfirmar');

        el.querySelector('#dialogoTitulo').textContent = opcoes.titulo;
        mensagem.textContent = opcoes.mensagem || '';
        mensagem.classList.toggle('d-none', !opcoes.mensagem);
        erro.classList.add('d-none');

        const campo = opcoes.campo;
        let controle = null;
        input.classList.add('d-none');
        texto.classList.add('d-none');
        grupo.classList.toggle('d-none', !campo);
        if (campo) {
            if (campo.tipo === 'texto') {
                controle = texto;
            } else {
                controle = input;
                input.type = 'number';
                input.step = 'any';
                input.min = '0';
            }
            el.querySelector('#dialogoRotulo').textContent = campo.rotulo;
            controle.placeholder = campo.placeholder || '';
            controle.value = '';
            controle.classList.remove('d-none');
        }

        botao.textContent = opcoes.confirmar || 'Confirmar';
        botao.className = 'btn ' + (opcoes.perigo ? 'btn-danger' : 'btn-primary');
        botao.disabled = false;

        botao.onclick = async () => {
            erro.classList.add('d-none');
            botao.disabled = true;
            let mensagemErro = null;
            try {
                mensagemErro = await opcoes.aoConfirmar(controle ? controle.value : null);
            } catch (e) {
                mensagemErro = 'Não foi possível concluir a operação.';
            }
            botao.disabled = false;

            if (mensagemErro) {
                erro.textContent = mensagemErro;
                erro.classList.remove('d-none');
            } else {
                modal.hide();
            }
        };

        input.onkeydown = (e) => { if (e.key === 'Enter') botao.click(); };

        el.addEventListener('shown.bs.modal', () => { if (controle) controle.focus(); }, { once: true });
        modal.show();
    }
};

async function lerMensagemErro(resposta, padrao) {
    if (resposta.status === 403) {
        return 'Você não tem permissão para realizar esta operação.';
    }
    try {
        const dados = await resposta.json();
        if (dados.erro) return dados.erro;
    } catch (e) { /* mantém a mensagem padrão */ }
    return padrao;
}

document.addEventListener('submit', (evento) => {
    const form = evento.target;
    const pergunta = form.dataset.confirmar;
    if (!pergunta) return;

    evento.preventDefault();
    Dialogo.abrir({
        titulo: 'Confirmar exclusão',
        mensagem: pergunta,
        confirmar: 'Excluir',
        perigo: true,
        aoConfirmar: () => { form.submit(); return null; }
    });
});

document.addEventListener('click', (evento) => {
    const link = evento.target.closest('a[data-confirmar]');
    if (!link) return;

    evento.preventDefault();
    Dialogo.abrir({
        titulo: 'Confirmar exclusão',
        mensagem: link.dataset.confirmar,
        confirmar: 'Excluir',
        perigo: true,
        aoConfirmar: () => { window.location.href = link.href; return null; }
    });
});