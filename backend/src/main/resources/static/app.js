const $ = (id) => document.getElementById(id);
const state = { api: '', access: '', refresh: '', user: null, tickets: [], categories: [], currentTicket: null, comments: [], history: [], users: [] };
const STATUS = ['ABERTO','EM_ATENDIMENTO','AGUARDANDO_USUARIO','AGUARDANDO_TERCEIRO','RESOLVIDO','FECHADO'];
const STATUS_LABEL = {ABERTO:'Aberto',EM_ATENDIMENTO:'Em atendimento',AGUARDANDO_USUARIO:'Aguardando usuário',AGUARDANDO_TERCEIRO:'Aguardando terceiro',RESOLVIDO:'Resolvido',FECHADO:'Fechado'};
const PRIORITY_LABEL = {BAIXA:'Baixa',MEDIA:'Média',ALTA:'Alta',CRITICA:'Crítica'};
let toastTimer;
function toast(message, error=false){const el=$('toast');el.textContent=message;el.className='toast show'+(error?' error':'');clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.className='toast',3800)}
function esc(v=''){return String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
function date(v){if(!v)return '—';const d=new Date(v);return Number.isNaN(d.getTime())?'—':d.toLocaleString('pt-BR',{dateStyle:'short',timeStyle:'short'})}
function statusBadge(v){return `<span class="badge status-${esc(v)}">${esc(STATUS_LABEL[v]||v||'—')}</span>`}
function priorityBadge(v){return `<span class="badge priority-${esc(v)}">${esc(PRIORITY_LABEL[v]||v||'—')}</span>`}
function role(){return state.user?.perfil||''}
function isAdmin(){return role()==='ADMIN'}
function isStaff(){return ['ADMIN','TECNICO'].includes(role())}
function showLogin(){ $('loginScreen').classList.remove('hidden');$('appShell').classList.add('hidden'); }
function showApp(){ $('loginScreen').classList.add('hidden');$('appShell').classList.remove('hidden');$('userName').textContent=state.user?.nome||'Usuário';$('userRole').textContent=role();$('userAvatar').textContent=(state.user?.nome||'U').trim().charAt(0).toUpperCase();$('todayLabel').textContent=new Date().toLocaleDateString('pt-BR',{weekday:'short',day:'2-digit',month:'short',year:'numeric'});document.querySelectorAll('.admin-only').forEach(x=>x.classList.toggle('hidden',!isAdmin()));}
async function rawRequest(path, options={}) {
  const headers={'Content-Type':'application/json',...(options.headers||{})};
  if(state.access && !options.public) headers.Authorization=`Bearer ${state.access}`;
  const response=await fetch(`${state.api}${path}`,{...options,headers});
  if(response.status===204)return null;
  const raw=await response.text();let data=null;try{data=raw?JSON.parse(raw):null}catch{data=raw}
  if(!response.ok){const msg=data?.mensagem||data?.message||data?.detail||data?.error||`Erro HTTP ${response.status}`;const e=new Error(msg);e.status=response.status;e.data=data;throw e}
  return data;
}
async function refreshToken(){
  if(!state.refresh)return false;
  try{const result=await rawRequest('/api/auth/refresh',{method:'POST',body:JSON.stringify({refreshToken:state.refresh}),public:true});state.access=result.accessToken;state.refresh=result.refreshToken;return true}catch{return false}
}
async function api(path, options={}) {
  try{return await rawRequest(path,options)}
  catch(e){if(e.status===401&&!options.public&&await refreshToken())return rawRequest(path,options);if(e.status===401){logout(false);throw new Error('Sessão expirada. Entre novamente.')}throw e}
}
function query(params){const q=new URLSearchParams();Object.entries(params).forEach(([k,v])=>{if(v!==''&&v!==null&&v!==undefined&&v!==false)q.set(k,v)});return q.toString()?`?${q}`:''}
async function login(email,senha){
  state.api=($('apiUrl').value.trim()||'http://localhost:8080').replace(/\/+$/,'');localStorage.setItem('chamati_api',state.api);
  const result=await rawRequest('/api/auth/login',{method:'POST',body:JSON.stringify({email,senha}),public:true});
  state.access=result.accessToken;state.refresh=result.refreshToken;state.user=result.usuario;
  localStorage.setItem('chamati_api',state.api);showApp();await loadInitial();navigate('dashboard');
}
async function logout(callApi=true){
  if(callApi&&state.refresh){try{await rawRequest('/api/auth/logout',{method:'POST',body:JSON.stringify({refreshToken:state.refresh}),public:true})}catch{}}
  state.access='';state.refresh='';state.user=null;state.tickets=[];showLogin();
}
async function loadInitial(){
  try{const [cats]=await Promise.all([api('/api/categorias/ativas')]);state.categories=cats||[];renderCategoryOptions();await loadTickets();}catch(e){toast(e.message,true)}
}
async function loadTickets(){
  const params={q:$('ticketSearch')?.value?.trim(),status:$('statusFilter')?.value,prioridade:$('priorityFilter')?.value,pagina:0,tamanho:100,ordenarPor:'criadoEm',direcao:'desc'};
  const result=await api('/api/chamados'+query(params));state.tickets=result?.conteudo||[];$('navTicketCount').textContent=result?.totalElementos??state.tickets.length;renderDashboard();renderTickets();
}
function renderDashboard(){
  const t=state.tickets;const count=s=>t.filter(x=>x.status===s).length;
  $('statTotal').textContent=t.length;$('statOpen').textContent=count('ABERTO');$('statProgress').textContent=t.filter(x=>['EM_ATENDIMENTO','AGUARDANDO_USUARIO','AGUARDANDO_TERCEIRO'].includes(x.status)).length;$('statResolved').textContent=count('RESOLVIDO');
  const recent=t.slice(0,6);$('recentTable').innerHTML=recent.length?ticketTable(recent):emptyState('Nenhum chamado encontrado','Quando houver solicitações, elas aparecerão aqui.');
}
function ticketTable(items){return `<table><thead><tr><th>Chamado</th><th>Solicitante</th><th>Categoria</th><th>Prioridade</th><th>Status</th><th>Abertura</th></tr></thead><tbody>${items.map(t=>`<tr><td><button class="ticket-link" data-ticket="${t.id}">${esc(t.titulo)}</button><span class="ticket-protocol">${esc(t.protocolo)}</span></td><td>${esc(t.solicitanteNome||'—')}</td><td>${esc(t.categoriaNome||'—')}</td><td>${priorityBadge(t.prioridade)}</td><td>${statusBadge(t.status)}</td><td>${date(t.criadoEm)}</td></tr>`).join('')}</tbody></table>`}
function renderTickets(){const items=state.tickets;$('ticketsTable').innerHTML=items.length?ticketTable(items):emptyState('Nenhum chamado encontrado','Tente alterar os filtros ou abra um novo chamado.')}
function emptyState(title,desc){return `<div class="empty"><strong>${esc(title)}</strong>${esc(desc||'')}</div>`}
function renderCategoryOptions(){const select=$('ticketCategory');select.innerHTML='<option value="">Selecione uma categoria</option>'+state.categories.map(c=>`<option value="${c.id}">${esc(c.nome)}</option>`).join('')}
async function loadSubcategories(categoryId){const select=$('ticketSubcategory');select.innerHTML='<option value="">Selecione (opcional)</option>';if(!categoryId)return;try{const rows=await api(`/api/categorias/${categoryId}/subcategorias`);select.innerHTML+=(rows||[]).filter(x=>x.ativo).map(x=>`<option value="${x.id}">${esc(x.nome)}</option>`).join('')}catch(e){toast(e.message,true)}}
function navigate(page){document.querySelectorAll('.page').forEach(p=>p.classList.add('hidden'));const target=$(`page-${page}`);if(target)target.classList.remove('hidden');document.querySelectorAll('.nav-item').forEach(b=>b.classList.toggle('active',b.dataset.page===page));const names={dashboard:'Visão geral',tickets:'Chamados','new-ticket':'Abrir chamado',detail:'Detalhes do chamado',users:'Usuários'};$('breadcrumbPage').textContent=names[page]||'Workspace';$('sidebar').classList.remove('open');if(page==='tickets')loadTickets().catch(e=>toast(e.message,true));if(page==='users')loadUsers().catch(e=>toast(e.message,true));}
async function openTicket(id){try{const [ticket,comments,hist]=await Promise.all([api(`/api/chamados/${id}`),api(`/api/chamados/${id}/comentarios?pagina=0&tamanho=100&direcao=asc`),api(`/api/chamados/${id}/historico?pagina=0&tamanho=100&direcao=asc`)]);state.currentTicket=ticket;state.comments=comments?.conteudo||[];state.history=hist?.conteudo||[];renderDetail();navigate('detail')}catch(e){toast(e.message,true)}}
function renderDetail(){
 const t=state.currentTicket;if(!t)return;const id=t.id;const canManage=isStaff();const canResolve=canManage&&['EM_ATENDIMENTO','AGUARDANDO_USUARIO','AGUARDANDO_TERCEIRO'].includes(t.status);const canClose=(isAdmin()||t.solicitante?.id===state.user?.id)&&t.status==='RESOLVIDO';const canReopen=(isAdmin()||t.solicitante?.id===state.user?.id||t.tecnico?.id===state.user?.id)&&['RESOLVIDO','FECHADO'].includes(t.status);
 $('ticketDetail').innerHTML=`
 <div class="detail-head"><div><span class="eyebrow">${esc(t.protocolo)}</span><h1>${esc(t.titulo)}</h1><p>Aberto em ${date(t.criadoEm)} · Atualizado em ${date(t.atualizadoEm)}</p></div><div class="detail-actions">${statusBadge(t.status)} ${priorityBadge(t.prioridade)}</div></div>
 <div class="detail-grid"><div class="detail-main">
  <section class="detail-card"><h3>Descrição do problema</h3><div class="detail-description">${esc(t.descricao)}</div></section>
  <section class="detail-card"><h3>Comentários e atualizações</h3><div>${state.comments.length?state.comments.map(c=>`<div class="comment"><div class="comment-top"><b>${esc(c.autor?.nome||'Usuário')} ${c.tipo==='NOTA_INTERNA'?'<span class="badge priority-ALTA">Nota interna</span>':''}</b><small>${date(c.criadoEm)}</small></div><p>${esc(c.conteudo)}</p></div>`).join(''):emptyState('Sem comentários','Ainda não há atualizações neste chamado.')}</div>${t.status!=='FECHADO'?`<form id="commentForm" class="comment-form"><textarea id="commentText" rows="3" required maxlength="10000" placeholder="Escreva uma atualização..."></textarea><div class="form-actions"><select id="commentType" style="max-width:160px"><option value="PUBLICO">Comentário público</option>${isStaff()?'<option value="NOTA_INTERNA">Nota interna</option>':''}</select><button class="btn btn-primary" type="submit">Enviar comentário →</button></div></form>`:''}</section>
  <section class="detail-card"><h3>Histórico de atividades</h3>${state.history.length?state.history.map(h=>`<div class="timeline-item"><span class="timeline-dot"></span><div><b>${esc((h.tipoEvento||'Evento').replaceAll('_',' '))}</b><small>${date(h.criadoEm)} · ${esc(h.autor?.nome||'Sistema')}</small><p>${esc(h.descricao||[h.campo,h.valorAnterior,h.valorNovo].filter(Boolean).join(' → '))}</p></div></div>`).join(''):emptyState('Sem histórico','')}</section>
 </div>
 <aside class="detail-side"><section class="detail-card"><h3>Detalhes</h3><div class="detail-meta"><div class="meta-item"><small>Solicitante</small><b>${esc(t.solicitante?.nome||'—')}</b></div><div class="meta-item"><small>Técnico responsável</small><b>${esc(t.tecnico?.nome||'Não atribuído')}</b></div><div class="meta-item"><small>Categoria</small><b>${esc(t.categoria?.nome||'—')}</b></div><div class="meta-item"><small>Subcategoria</small><b>${esc(t.subcategoria?.nome||'—')}</b></div><div class="meta-item"><small>Prioridade</small><b>${esc(PRIORITY_LABEL[t.prioridade]||t.prioridade)}</b></div><div class="meta-item"><small>Reaberturas</small><b>${t.totalReaberturas??0}</b></div><div class="meta-item"><small>SLA de resposta</small><b>${esc(t.situacaoSlaPrimeiraResposta||'—')}</b></div><div class="meta-item"><small>SLA de resolução</small><b>${esc(t.situacaoSlaResolucao||'—')}</b></div></div></section>
 ${canManage?`<section class="detail-card"><h3>Gerenciar chamado</h3><div class="side-actions">${isAdmin()?`<label>Técnico (ID do usuário)</label><input id="technicianId" type="number" min="1" placeholder="Ex.: 2"><button class="btn btn-outline" id="assignTech">Atribuir técnico</button>`:''}${role()==='TECNICO'&&!t.tecnico?'<button class="btn btn-outline" id="takeTicket">Assumir este chamado</button>':''}<label>Alterar status</label><select id="nextStatus">${STATUS.filter(s=>s!==t.status&&s!=='RESOLVIDO'&&s!=='FECHADO').map(s=>`<option value="${s}">${STATUS_LABEL[s]}</option>`).join('')}</select><textarea id="statusNote" rows="2" placeholder="Observação (opcional)"></textarea><button class="btn btn-outline" id="changeStatus">Atualizar status</button>${canResolve?'<button class="btn btn-primary" id="resolveTicket">✓ Resolver chamado</button>':''}</div></section>`:''}
 ${canClose||canReopen?`<section class="detail-card"><h3>Outras ações</h3><div class="side-actions">${canClose?'<button class="btn btn-primary" id="closeTicket">Encerrar chamado</button>':''}${canReopen?'<textarea id="reopenReason" rows="2" placeholder="Motivo da reabertura"></textarea><button class="btn btn-outline" id="reopenTicket">Reabrir chamado</button>':''}</div></section>`:''}
 </aside></div>`;
 $('commentForm')?.addEventListener('submit',sendComment);$('assignTech')?.addEventListener('click',assignTech);$('takeTicket')?.addEventListener('click',takeTicket);$('changeStatus')?.addEventListener('click',changeStatus);$('resolveTicket')?.addEventListener('click',resolveTicket);$('closeTicket')?.addEventListener('click',closeTicket);$('reopenTicket')?.addEventListener('click',reopenTicket);
}
async function sendComment(e){e.preventDefault();try{await api(`/api/chamados/${state.currentTicket.id}/comentarios`,{method:'POST',body:JSON.stringify({tipo:$('commentType').value,conteudo:$('commentText').value.trim()})});toast('Comentário enviado.');await openTicket(state.currentTicket.id)}catch(err){toast(err.message,true)}}
async function assignTech(){const tecnicoId=Number($('technicianId').value);if(!tecnicoId)return toast('Informe o ID de um usuário técnico.',true);try{await api(`/api/chamados/${state.currentTicket.id}/tecnico`,{method:'PUT',body:JSON.stringify({tecnicoId,versao:state.currentTicket.versao})});toast('Técnico atribuído.');await openTicket(state.currentTicket.id)}catch(e){toast(e.message,true)}}
async function takeTicket(){try{await api(`/api/chamados/${state.currentTicket.id}/tecnico`,{method:'PUT',body:JSON.stringify({tecnicoId:state.user.id,versao:state.currentTicket.versao})});toast('Chamado atribuído a você.');await openTicket(state.currentTicket.id)}catch(e){toast(e.message,true)}}
async function changeStatus(){try{await api(`/api/chamados/${state.currentTicket.id}/status`,{method:'PATCH',body:JSON.stringify({status:$('nextStatus').value,observacao:$('statusNote').value.trim()||null,versao:state.currentTicket.versao})});toast('Status atualizado.');await openTicket(state.currentTicket.id)}catch(e){toast(e.message,true)}}
async function resolveTicket(){const solucao=prompt('Descreva a solução aplicada:');if(!solucao?.trim())return;try{await api(`/api/chamados/${state.currentTicket.id}/resolver`,{method:'POST',body:JSON.stringify({solucao:solucao.trim(),versao:state.currentTicket.versao})});toast('Chamado resolvido.');await openTicket(state.currentTicket.id)}catch(e){toast(e.message,true)}}
async function closeTicket(){if(!confirm('Deseja encerrar este chamado?'))return;try{await api(`/api/chamados/${state.currentTicket.id}/fechar`,{method:'POST',body:JSON.stringify({versao:state.currentTicket.versao})});toast('Chamado encerrado.');await openTicket(state.currentTicket.id)}catch(e){toast(e.message,true)}}
async function reopenTicket(){const motivo=$('reopenReason').value.trim();if(!motivo)return toast('Informe o motivo da reabertura.',true);try{await api(`/api/chamados/${state.currentTicket.id}/reabrir`,{method:'POST',body:JSON.stringify({motivo,versao:state.currentTicket.versao})});toast('Chamado reaberto.');await openTicket(state.currentTicket.id)}catch(e){toast(e.message,true)}}
async function createTicket(e){e.preventDefault();const payload={titulo:$('ticketTitle').value.trim(),descricao:$('ticketDescription').value.trim(),categoriaId:Number($('ticketCategory').value),subcategoriaId:$('ticketSubcategory').value?Number($('ticketSubcategory').value):null,prioridade:$('ticketPriority').value};try{const t=await api('/api/chamados',{method:'POST',body:JSON.stringify(payload)});toast(`Chamado ${t.protocolo} aberto com sucesso.`);$('newTicketForm').reset();$('ticketSubcategory').innerHTML='<option value="">Selecione (opcional)</option>';await loadTickets();await openTicket(t.id)}catch(err){toast(err.message,true)}}
async function loadUsers(){if(!isAdmin())return;const q=$('userSearch').value.trim();const result=await api('/api/usuarios'+query({q,pagina:0,tamanho:100}));state.users=result?.conteudo||[];$('usersTable').innerHTML=state.users.length?`<table><thead><tr><th>Nome</th><th>E-mail</th><th>Perfil</th><th>Departamento</th><th>Situação</th></tr></thead><tbody>${state.users.map(u=>`<tr><td><b>${esc(u.nome)}</b></td><td>${esc(u.email)}</td><td>${esc(u.perfil)}</td><td>${esc(u.departamento||'—')}</td><td>${u.ativo?'<span class="badge status-RESOLVIDO">Ativo</span>':'<span class="badge status-FECHADO">Inativo</span>'}</td></tr>`).join('')}</tbody></table>`:emptyState('Nenhum usuário encontrado','')}
$('loginForm').addEventListener('submit',async e=>{e.preventDefault();$('loginError').textContent='';$('apiUrl').value=($('apiUrl').value.trim()||'http://localhost:8080');try{await login($('email').value.trim(),$('password').value)}catch(err){$('loginError').textContent=err.message}});
$('apiUrl').value=localStorage.getItem('chamati_api')||`${location.protocol}//${location.hostname}:8080`;
document.querySelectorAll('[data-page]').forEach(b=>b.addEventListener('click',()=>navigate(b.dataset.page)));
document.querySelectorAll('[data-go]').forEach(b=>b.addEventListener('click',()=>navigate(b.dataset.go)));
document.addEventListener('click',e=>{const b=e.target.closest('[data-ticket]');if(b)openTicket(Number(b.dataset.ticket))});
$('logoutBtn').addEventListener('click',()=>logout());$('mobileMenu').addEventListener('click',()=>$('sidebar').classList.toggle('open'));
$('ticketCategory').addEventListener('change',e=>loadSubcategories(e.target.value));
$('newTicketForm').addEventListener('submit',createTicket);
$('ticketSearch').addEventListener('input',debounce(()=>loadTickets().catch(e=>toast(e.message,true)),350));
$('statusFilter').addEventListener('change',()=>loadTickets().catch(e=>toast(e.message,true)));
$('priorityFilter').addEventListener('change',()=>loadTickets().catch(e=>toast(e.message,true)));
$('refreshTickets').addEventListener('click',()=>loadTickets().catch(e=>toast(e.message,true)));
$('backToTickets').addEventListener('click',()=>navigate('tickets'));
$('refreshUsers').addEventListener('click',()=>loadUsers().catch(e=>toast(e.message,true)));
$('userSearch').addEventListener('input',debounce(()=>loadUsers().catch(e=>toast(e.message,true)),350));
function debounce(fn,wait){let timer;return(...args)=>{clearTimeout(timer);timer=setTimeout(()=>fn(...args),wait)}}
