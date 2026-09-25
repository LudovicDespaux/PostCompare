import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { ArrowRight, ArrowUpDown, Check, ChevronDown, Clock3, Globe2, Mail, MapPin, Printer, Send, ShieldCheck, Sparkles } from 'lucide-react';
import './style.css';

type Country = { code: string; name: string };
type Quote = { id: string; provider: string; method: string; price: number; currency: string; minDays: number; maxDays: number; tracking: boolean; description: string; priceScope: string };
const initial = { origin: 'FR', destination: 'RO', pages: 2, weight: 20, color: false, duplex: true, tracking: false };
const money = (n: number) => new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(n);

function App() {
  const [countries, setCountries] = useState<Country[]>([]);
  const [form, setForm] = useState(initial);
  const [quotes, setQuotes] = useState<Quote[] | null>(null);
  const [route, setRoute] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [countryError, setCountryError] = useState(false);
  const [retry, setRetry] = useState(0);
  const [method, setMethod] = useState('ALL');
  const [sort, setSort] = useState('price');
  const [details, setDetails] = useState<string | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    setCountryError(false);
    fetch('/api/countries', { signal: controller.signal }).then(r => {
      if (!r.ok) throw new Error(); return r.json();
    }).then(setCountries).catch(e => { if (e.name !== 'AbortError') setCountryError(true); });
    return () => controller.abort();
  }, [retry]);
  function update<K extends keyof typeof initial>(key: K, value: typeof initial[K]) {
    setForm(f => ({ ...f, [key]: value })); setQuotes(null); setError(''); setDetails(null);
  }
  const name = (code: string) => countries.find(c => c.code === code)?.name ?? code;
  async function compare(e: React.FormEvent) {
    e.preventDefault(); setLoading(true); setError(''); setQuotes(null); setDetails(null);
    try {
      const response = await fetch('/api/quotes', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form), signal: AbortSignal.timeout(15000) });
      if (!response.ok) throw new Error(response.status === 400 ? 'Vérifiez les pays, le nombre de pages et le poids.' : 'Le service est indisponible. Réessayez dans un instant.');
      const data = await response.json(); setQuotes(data.quotes); setRoute(`${name(form.origin)} → ${name(form.destination)}`);
    } catch (e) { setError(e instanceof Error && e.name === 'Error' ? e.message : 'Connexion impossible. Vérifiez que le serveur est démarré, puis réessayez.'); }
    finally { setLoading(false); }
  }
  const visible = quotes?.filter(q => method === 'ALL' || q.method === method).sort((a,b) => sort === 'price' ? a.price-b.price : a.maxDays-b.maxDays || a.price-b.price) ?? [];
  const cheapest = visible.length ? Math.min(...visible.map(q => q.price)) : 0;
  return <>
    <header><a className="brand" href="#"><span className="brand-icon"><Send size={20}/></span>PostCompare<span className="beta">BÊTA</span></a><nav><a href="#comment">Comment ça marche</a><a href="#comparateur" className="nav-cta">Comparer un courrier <ArrowRight size={15}/></a></nav></header>
    <main>
      <section className="hero"><div className="hero-copy"><div className="eyebrow"><span/> UNE LETTRE. PLUSIEURS CHEMINS.</div><h1>Votre courrier,<br/>au <em>bon prix.</em></h1><p>À deux rues ou à l’autre bout du monde.<br/>Explorez les façons d’envoyer votre lettre et trouvez celle qui vous convient.</p><div className="hero-points"><span><Check size={16}/> Comparaison gratuite</span><span><Check size={16}/> Sans inscription</span></div></div><div className="journey" aria-hidden="true"><div className="orbit orbit-one"/><div className="orbit orbit-two"/><div className="map-dot dot-a"/><div className="map-dot dot-b"/><div className="journey-label from"><span>AU DÉPART</span><strong>Bonjour, le monde.</strong><MapPin size={18}/></div><div className="envelope"><div className="stamp"><Globe2 size={28}/><span>PAR AVION</span></div><div className="address-lines"><i/><i/><i/></div><span className="envelope-brand">postcompare /</span></div><div className="journey-label to"><span>À L’ARRIVÉE</span><strong>Le meilleur chemin.</strong><Check size={18}/></div><div className="flight"><Send size={28}/></div></div></section>
      <section className="workspace" id="comparateur"><form className="form-card" onSubmit={compare}><div className="card-title"><span className="step">01</span><h2>Votre courrier</h2><Mail size={20}/></div><fieldset disabled={loading}><legend className="sr-only">Caractéristiques de votre courrier</legend><label>Pays de départ<div className="select-wrap"><MapPin size={17}/><select value={form.origin} onChange={e => update('origin',e.target.value)} aria-label="Pays de départ">{countries.map(c => <option key={c.code} value={c.code}>{c.name}</option>)}</select></div></label><div className="route-divider"><span/><button type="button" aria-label="Inverser les pays" onClick={() => { setForm(f => ({...f,origin:f.destination,destination:f.origin})); setQuotes(null); }}><ArrowUpDown size={16}/></button><span/></div><label>Pays de destination<div className="select-wrap"><Globe2 size={17}/><select value={form.destination} onChange={e => update('destination',e.target.value)} aria-label="Pays de destination">{countries.map(c => <option key={c.code} value={c.code}>{c.name}</option>)}</select></div></label>{countryError && <div role="alert" className="error">Impossible de charger les pays. <button type="button" onClick={() => setRetry(v => v+1)}>Réessayer</button></div>}<div className="form-line"/><div className="input-row"><label>Pages A4<input type="number" min="1" max="50" required value={form.pages} onChange={e => update('pages',e.target.valueAsNumber)}/></label><label>Poids de la lettre (g)<input type="number" min="1" max="500" required value={form.weight} onChange={e => update('weight',e.target.valueAsNumber)}/></label></div><p className="hint">Poids total avec enveloppe pour un dépôt postal.</p><label className="checkbox"><input type="checkbox" checked={form.color} onChange={e => update('color',e.target.checked)}/> Impression couleur</label><label className="checkbox"><input type="checkbox" checked={form.duplex} onChange={e => update('duplex',e.target.checked)}/> Impression recto verso</label><label className="checkbox"><input type="checkbox" checked={form.tracking} onChange={e => update('tracking',e.target.checked)}/> Avec suivi du courrier</label><button className="compare" disabled={loading || !countries.length}>{loading ? 'Comparaison en cours…' : 'Comparer les solutions'}<ArrowRight size={18}/></button></fieldset><p className="privacy"><ShieldCheck size={14}/> Aucune adresse personnelle nécessaire</p></form>
      <div className="results"><div className="demo-banner"><Sparkles size={20}/><div><strong>Un aperçu de ce qui est possible</strong><p>Mode démo : prestataires, prix et délais fictifs. Aucun envoi réel.</p></div><span>DÉMO</span></div><div className="results-title"><div><div className="eyebrow small">LES CHEMINS POSSIBLES</div><h2>{quotes ? `${visible.length} solution${visible.length > 1 ? 's' : ''} pour votre lettre` : 'Le monde vous attend.'}</h2></div>{quotes && <select aria-label="Trier les résultats" value={sort} onChange={e => setSort(e.target.value)}><option value="price">Prix croissant</option><option value="speed">Délai le plus court</option></select>}</div>{quotes && <><p className="route-summary">{route} · {form.pages} pages · {form.tracking ? 'Avec suivi' : 'Sans suivi'}</p><div className="tabs" aria-label="Mode d’envoi">{[['ALL','Tout comparer'],['PRINT_AND_MAIL','Impression + envoi'],['SELF_POST','Dépôt postal']].map(([id,label]) => <button key={id} className={method===id?'active':''} onClick={() => setMethod(id)} aria-pressed={method===id}>{label}</button>)}</div><p className="scope-note">Le dépôt postal comprend le port seul. L’impression + envoi inclut aussi la préparation.</p></>}
      <div aria-live="polite" aria-busy={loading}>{error && <p role="alert" className="error">{error}</p>}{loading && <div className="empty"><div className="loading-ring"/><h3>Nous explorons les itinéraires…</h3></div>}{!quotes && !loading && !error && <div className="empty"><div className="empty-icon"><Mail size={36}/><span><Globe2 size={19}/></span></div><h3>Une destination. Plusieurs possibilités.</h3><p>Renseignez votre courrier pour comparer le dépôt postal<br className="desktop"/> et les solutions d’impression avec envoi.</p><div className="empty-route"><span>Vous</span><i/><Send size={20}/><i/><span>Votre destinataire</span></div></div>}{quotes && !visible.length && <div className="empty"><Mail size={32}/><h3>Aucune solution pour ces critères</h3><p>Essayez un autre mode d’envoi ou ajustez le poids de votre lettre.</p></div>}{visible.map(q => <article className={`quote-card ${q.price===cheapest?'best':''}`} key={q.id}>{q.price===cheapest && <div className="best-label"><Sparkles size={12}/> LE MOINS CHER DE CETTE SÉLECTION</div>}<div className="quote-main"><div className={`provider-icon ${q.method==='SELF_POST'?'postal':''}`}>{q.method==='SELF_POST'?<Mail size={24}/>:<Printer size={24}/>}</div><div className="quote-name"><h3>{q.provider}</h3><p>{q.method==='SELF_POST'?'À déposer vous-même':'Impression + envoi'}</p></div><div className="price"><strong>{money(q.price)}</strong><span>prix simulé</span></div></div><div className="quote-meta"><span><Clock3 size={14}/>{q.minDays}–{q.maxDays} jours ouvrés</span><span>{q.tracking?<ShieldCheck size={14}/>:<Check size={14}/>} {q.tracking?'Suivi inclus':'Standard'}</span><button aria-expanded={details===q.id} onClick={() => setDetails(details===q.id?null:q.id)}>Détails <ChevronDown size={14}/></button></div>{details===q.id && <div className="quote-details"><p>{q.description}</p><p><strong>Inclus :</strong> {q.priceScope}.</p><p>Simulation sans valeur commerciale. Tarifs, taxes, disponibilité et délais à confirmer lors du branchement d’un prestataire réel.</p></div>}</article>)}</div><div className="results-foot"><ShieldCheck size={16}/><span>Vos documents restent chez vous. Aucun fichier à transmettre pour comparer.</span></div></div></section>
      <section id="comment" className="how"><div><div className="eyebrow small">DU NUMÉRIQUE À LA BOÎTE AUX LETTRES</div><h2>Le courrier prend un nouveau chemin.</h2></div><div className="how-grid"><article><span>01 /</span><h3>Décrivez votre lettre</h3><p>Deux pays, quelques caractéristiques.<br/>C’est tout ce qu’il faut pour commencer.</p></article><article><span>02 /</span><h3>Explorez les solutions</h3><p>Comparez les prix, les délais et ce qui est inclus dans chaque méthode.</p></article><article><span>03 /</span><h3>Choisissez votre chemin</h3><p>À terme, passez au prestataire choisi.<br/>Pour l’instant, testez le comparateur en démo.</p></article></div></section>
    </main><footer><a className="brand" href="#"><Send size={18}/>PostCompare</a><span>Un petit courrier. Un monde de possibilités.</span><span>Prototype · 2026</span></footer>
  </>;
}
createRoot(document.getElementById('root')!).render(<React.StrictMode><App/></React.StrictMode>);
