import { useState, useEffect } from 'react'
import './App.css'

const API_BASE = 'http://localhost:8080'

function App() {
  const [view, setView] = useState('store')
  const [products, setProducts] = useState([])
  const [orders, setOrders] = useState([])
  const [payments, setPayments] = useState([])
  const [shipments, setShipments] = useState([])
  const [loading, setLoading] = useState(false)
  const [logs, setLogs] = useState(["[SYSTEM] Initialized PC Master Admin Console..."])

  const [newProduct, setNewProduct] = useState({
    id: '',
    name: '',
    price: '',
    quantity: '',
    category: 'Hardware'
  })

  const [newOrder, setNewOrder] = useState({
    id: '',
    usuarioId: 'MANUAL-USER',
    productosIds: '',
    total: ''
  })

  const [newPayment, setNewPayment] = useState({
    id: '',
    ordenId: '',
    monto: ''
  })

  useEffect(() => {
    fetchProducts()
    fetchOrders()
    fetchPayments()
    fetchShipments()

    const interval = setInterval(() => {
      fetchOrders()
      fetchPayments()
      fetchShipments()
    }, 3000)
    return () => clearInterval(interval)
  }, [])

  const addLog = (msg) => {
    setLogs(prev => [...prev, `[${new Date().toLocaleTimeString()}] ${msg}`].slice(-8))
  }

  const fetchProducts = async () => {
    try {
      const res = await fetch(`${API_BASE}/productos`)
      const data = await res.json()
      setProducts(Array.isArray(data) ? data : [])
    } catch (err) {
      addLog("ERROR: Failed to fetch products")
      setProducts([])
    }
  }

  const fetchOrders = async () => {
    try {
      const res = await fetch(`${API_BASE}/ordenes`)
      const data = await res.json()
      setOrders(Array.isArray(data) ? data : [])
    } catch (err) {
      addLog("ERROR: Failed to fetch orders")
      setOrders([])
    }
  }

  const fetchPayments = async () => {
    try {
      const res = await fetch(`${API_BASE}/pagos`)
      const data = await res.json()
      setPayments(Array.isArray(data) ? data : [])
    } catch (err) {
      addLog("ERROR: Failed to fetch payments")
      setPayments([])
    }
  }

  const fetchShipments = async () => {
    try {
      const res = await fetch(`${API_BASE}/ordenes/envios`)
      const data = await res.json()
      setShipments(Array.isArray(data) ? data : [])
    } catch (err) {
      addLog("ERROR: Failed to fetch shipments")
      setShipments([])
    }
  }

  const handleAddProduct = async (e) => {
    e.preventDefault()
    const productToSend = {
      ...newProduct,
      price: parseFloat(newProduct.price),
      quantity: parseInt(newProduct.quantity)
    }
    try {
      const res = await fetch(`${API_BASE}/productos`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(productToSend)
      })
      if (res.ok) {
        addLog(`SUCCESS: Product ${productToSend.id} saved`)
        setNewProduct({ id: '', name: '', price: '', quantity: '', category: 'Hardware' })
        fetchProducts()
      } else {
        const msg = await res.text()
        addLog(`RETRY: ${msg}`)
      }
    } catch (err) {
      addLog("ERROR: Connection failed")
    }
  }

  const handleManualOrder = async (e) => {
    e.preventDefault()
    const orderBody = {
      id: newOrder.id || undefined,
      usuarioId: newOrder.usuarioId,
      productosIds: newOrder.productosIds.split(',').map(id => id.trim()),
      total: parseFloat(newOrder.total)
    }
    try {
      const res = await fetch(`${API_BASE}/ordenes`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(orderBody)
      })
      if (res.ok) {
        addLog(`SUCCESS: Order ${newOrder.id || 'NEW'} saved`)
        setNewOrder({ id: '', usuarioId: 'MANUAL-USER', productosIds: '', total: '' })
        fetchOrders()
        fetchProducts()
      } else {
        const msg = await res.text()
        addLog(`RETRY: ${msg}`)
      }
    } catch (err) {
      addLog("ERROR: Connection failed")
    }
  }

  const handleManualPayment = async (e) => {
    e.preventDefault()
    const paymentBody = {
      id: newPayment.id || undefined,
      ordenId: newPayment.ordenId,
      monto: parseFloat(newPayment.monto)
    }
    try {
      const res = await fetch(`${API_BASE}/pagos/procesar`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(paymentBody)
      })
      if (res.ok) {
        addLog(`SUCCESS: Payment for ${newPayment.ordenId} saved`)
        setNewPayment({ id: '', ordenId: '', monto: '' })
        fetchPayments()
        fetchOrders()
        fetchShipments()
      } else {
        const msg = await res.text()
        addLog(`RETRY: ${msg}`)
      }
    } catch (err) {
      addLog("ERROR: Connection failed")
    }
  }

  const handleRefund = async (id) => {
    try {
      const res = await fetch(`${API_BASE}/pagos/${id}/reembolso`, {
        method: 'PUT'
      })
      if (res.ok) {
        addLog(`SUCCESS: Payment ${id} refunded`)
        fetchPayments()
      } else {
        const msg = await res.text()
        addLog(`ERROR: ${msg}`)
      }
    } catch (err) {
      addLog("ERROR: Refund failed")
    }
  }

  const handleDeleteProduct = async (id) => {
    try {
      const res = await fetch(`${API_BASE}/productos/${id}`, { method: 'DELETE' })
      if (res.ok) {
        addLog(`SUCCESS: Product ${id} deleted`)
        fetchProducts()
      } else {
        const msg = await res.text()
        addLog(`ERROR: ${msg}`)
      }
    } catch (err) {
      addLog("ERROR: Delete failed")
    }
  }

  const handleDeleteOrder = async (id) => {
    try {
      const res = await fetch(`${API_BASE}/ordenes/${id}`, { method: 'DELETE' })
      if (res.ok) {
        addLog(`SUCCESS: Order ${id} deleted`)
        fetchOrders()
      } else {
        const msg = await res.text()
        addLog(`ERROR: ${msg}`)
      }
    } catch (err) {
      addLog("ERROR: Delete failed")
    }
  }

  const handleDeletePayment = async (id) => {
    try {
      const res = await fetch(`${API_BASE}/pagos/${id}`, { method: 'DELETE' })
      if (res.ok) {
        addLog(`SUCCESS: Payment ${id} deleted`)
        fetchPayments()
      } else {
        const msg = await res.text()
        addLog(`ERROR: ${msg}`)
      }
    } catch (err) {
      addLog("ERROR: Delete failed")
    }
  }

  return (
    <div className="app-container">
      <header>
        <h1>PC MASTER ADMIN <span className="cursor"></span></h1>
        <nav>
          <button className={view === 'store' ? 'active' : ''} onClick={() => setView('store')}>INVENTARIO</button>
          <button className={view === 'admin_products' ? 'active' : ''} onClick={() => setView('admin_products')}>PRODUCTOS</button>
          <button className={view === 'admin_orders' ? 'active' : ''} onClick={() => setView('admin_orders')}>ORDENES</button>
          <button className={view === 'admin_payments' ? 'active' : ''} onClick={() => setView('admin_payments')}>PAGOS</button>
          <button className={view === 'shipments' ? 'active' : ''} onClick={() => setView('shipments')}>ENVÍOS</button>
        </nav>
      </header>

      <section className="terminal-logs">
        {logs.map((log, i) => <div key={i} className="log-line">{log}</div>)}
      </section>

      <main>
        {view === 'store' && (
          <div className="product-grid">
            {products.map(p => (
              <div key={p.id} className="product-card">
                <span className="brand">ID: {p.id}</span>
                <h3>{p.name}</h3>
                <div className="price">${p.price}</div>
                <div className="stock">STOCK: {p.quantity}</div>
              </div>
            ))}
          </div>
        )}

        {view === 'admin_products' && (
          <div className="admin-panel">
            <h2>&gt; PRODUCT_MANAGEMENT (MONGO)</h2>
            <form className="admin-form" onSubmit={handleAddProduct}>
              <input placeholder="ID (ex: CPU-001)" value={newProduct.id} onChange={e => setNewProduct({...newProduct, id: e.target.value})} required />
              <input placeholder="Name" value={newProduct.name} onChange={e => setNewProduct({...newProduct, name: e.target.value})} required />
              <input type="number" placeholder="Price" value={newProduct.price} onChange={e => setNewProduct({...newProduct, price: e.target.value})} required />
              <input type="number" placeholder="Stock" value={newProduct.quantity} onChange={e => setNewProduct({...newProduct, quantity: e.target.value})} required />
              <button type="submit" className="full-width">REGISTER_PRODUCT</button>
            </form>

            <h3>&gt; EXISTING_PRODUCTS</h3>
            <div className="admin-product-list">
              {products.map(p => (
                <div key={p.id} className="admin-product-item">
                  <span>{p.id} - {p.name} (${p.price}) [STOCK: {p.quantity}]</span>
                  <button className="delete-btn-small" onClick={() => handleDeleteProduct(p.id)}>X</button>
                </div>
              ))}
            </div>
          </div>
        )}

        {view === 'admin_orders' && (
          <div className="admin-panel">
            <h2>&gt; ORDER_MANAGEMENT (MONGO)</h2>
            <form className="admin-form" onSubmit={handleManualOrder}>
              <input placeholder="ID (Optional)" value={newOrder.id} onChange={e => setNewOrder({...newOrder, id: e.target.value})} />
              <input placeholder="User ID" value={newOrder.usuarioId} onChange={e => setNewOrder({...newOrder, usuarioId: e.target.value})} required />
              <input placeholder="Product IDs (comma separated)" className="full-width" value={newOrder.productosIds} onChange={e => setNewOrder({...newOrder, productosIds: e.target.value})} required />
              <input type="number" placeholder="Total" className="full-width" value={newOrder.total} onChange={e => setNewOrder({...newOrder, total: e.target.value})} required />
              <button type="submit" className="full-width">REGISTER_ORDER</button>
            </form>

            <h3>&gt; EXISTING_ORDERS</h3>
            <div className="order-list">
              {orders.map(o => {
                const isPaid = o.status === 'PAID' || o.status === 'Pagado';
                const displayStatus = isPaid ? 'PAGADO' : 'POR PAGAR';
                const statusClass = isPaid ? 'status-paid' : 'status-pending';

                return (
                  <div key={o.id} className="order-item-card">
                    <div className="order-header">
                      <strong>ID: {o.id}</strong>
                      <span className={`status ${statusClass}`}>{displayStatus}</span>
                    </div>
                    <div className="order-details">
                      <span>User: {o.usuarioId}</span><br/>
                      <span>Total: ${o.total}</span><br/>
                      <span style={{fontSize: '0.7rem', color: '#64748b'}}>Status: {o.status}</span>
                    </div>
                    <div className="order-actions">
                      <button className="delete-btn" onClick={() => handleDeleteOrder(o.id)}>DELETE</button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {view === 'admin_payments' && (
          <div className="admin-panel">
            <h2>&gt; PAYMENT_MANAGEMENT (MONGO)</h2>
            <form className="admin-form" onSubmit={handleManualPayment}>
              <input placeholder="ID (Optional)" value={newPayment.id} onChange={e => setNewPayment({...newPayment, id: e.target.value})} />
              <input placeholder="Order ID" value={newPayment.ordenId} onChange={e => setNewPayment({...newPayment, ordenId: e.target.value})} required />
              <input type="number" placeholder="Amount" className="full-width" value={newPayment.monto} onChange={e => setNewPayment({...newPayment, monto: e.target.value})} required />
              <button type="submit" className="full-width">PROCESS_PAYMENT_MANUAL</button>
            </form>

            <h3>&gt; EXISTING_PAYMENTS</h3>
            <div className="order-list">
              {payments.map(p => (
                <div key={p.id} className="order-item-card">
                  <div className="order-header">
                    <strong>PAGO: {p.id}</strong>
                    <span className={`status status-${p.status ? p.status.toLowerCase() : 'unknown'}`}>{p.status}</span>
                  </div>
                  <div className="order-details">
                    <span>Orden: {p.ordenId}</span><br/>
                    <span>Monto: ${p.monto}</span>
                  </div>
                  <div className="order-actions">
                    <button onClick={() => handleRefund(p.id)} disabled={p.status === 'REEMBOLSADO'}>REEMBOLSO</button>
                    <button className="delete-btn" onClick={() => handleDeletePayment(p.id)}>DELETE</button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {view === 'shipments' && (
          <div className="admin-panel">
            <h2>&gt; PAID_ORDERS_LOGISTICS (POSTGRES)</h2>
            <div className="order-list">
              {shipments.length === 0 && (
                <div className="terminal-line" style={{flexDirection: 'column', alignItems: 'center', padding: '40px'}}>
                  <p>WAITING_FOR_PAID_ORDERS...</p>
                  <p style={{fontSize: '0.8rem', color: '#64748b'}}>Go to PAGOS to process a pending order.</p>
                </div>
              )}
              {shipments.map(s => (
                <div key={s.id} className="order-item-card">
                  <div className="order-header">
                    <strong style={{color: '#fff'}}>GUÍA_ID: {s.id}</strong>
                    <span className={`status ${s.status === 'SENT' ? 'status-paid' : 'status-pending'}`}>
                      {s.status === 'SENT' ? 'DESPACHADO' : 'EN_PROCESO'}
                    </span>
                  </div>
                  <div className="order-details">
                    <span style={{color: 'var(--accent-color)'}}>ORDEN_REF:</span> {s.ordenId}<br/>
                    <span style={{color: 'var(--accent-color)'}}>STATUS:</span> {s.status}<br/>
                    <span style={{color: 'var(--accent-color)'}}>FECHA:</span> {s.sentAt ? new Date(s.sentAt).toLocaleString() : 'PENDIENTE_ENVIO'}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </main>
    </div>
  )
}

export default App
