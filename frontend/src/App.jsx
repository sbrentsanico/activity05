import { useEffect, useState, useCallback } from "react";
import "./App.css";

const API_BASE = "http://localhost:8080/api";
const LOW_STOCK_THRESHOLD = 5;

export default function App() {
  const [products, setProducts] = useState([]);
  const [orders, setOrders] = useState([]);
  const [notifications, setNotifications] = useState([]);

  // Cart state
  const [selectedProductId, setSelectedProductId] = useState("");
  const [quantity, setQuantity] = useState(1);
  const [cart, setCart] = useState([]);

  // UI state
  const [submitting, setSubmitting] = useState(false);
  const [cancellingId, setCancellingId] = useState(null);
  const [lastResult, setLastResult] = useState(null);
  const [error, setError] = useState(null);

  // Fetch all dashboard data
  const fetchData = useCallback(async () => {
    try {
      const [invRes, ordRes, notifRes] = await Promise.all([
        fetch(`${API_BASE}/inventory`),
        fetch(`${API_BASE}/orders`),
        fetch(`${API_BASE}/notifications`),
      ]);

      if (invRes.ok) {
        const invData = await invRes.json();
        setProducts(invData);
        if (!selectedProductId && invData.length > 0) {
          setSelectedProductId(invData[0].productId);
        }
      }

      if (ordRes.ok) {
        const ordData = await ordRes.json();
        setOrders(ordData);
      }

      if (notifRes.ok) {
        const notifData = await notifRes.json();
        setNotifications(notifData);
      }
      setError(null);
    } catch (err) {
      setError("Unable to connect to backend on http://localhost:8080. Ensure Spring Boot is running.");
    }
  }, [selectedProductId]);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 4000);
    return () => clearInterval(interval);
  }, [fetchData]);

  // Cart actions
  const handleAddToCart = (e) => {
    e.preventDefault();
    if (!selectedProductId || quantity < 1) return;

    const prod = products.find((p) => p.productId === selectedProductId);
    if (!prod) return;

    setCart((prev) => {
      const existing = prev.find((item) => item.productId === selectedProductId);
      if (existing) {
        return prev.map((item) =>
          item.productId === selectedProductId
            ? { ...item, quantity: item.quantity + Number(quantity) }
            : item
        );
      } else {
        return [...prev, { productId: prod.productId, name: prod.name, quantity: Number(quantity) }];
      }
    });
    setQuantity(1);
  };

  const handleRemoveFromCart = (productId) => {
    setCart((prev) => prev.filter((item) => item.productId !== productId));
  };

  const handleUpdateCartQty = (productId, newQty) => {
    if (newQty <= 0) {
      handleRemoveFromCart(productId);
      return;
    }
    setCart((prev) =>
      prev.map((item) =>
        item.productId === productId ? { ...item, quantity: newQty } : item
      )
    );
  };

  const handleClearCart = () => {
    setCart([]);
  };

  // Submit multi-item order
  const handlePlaceOrder = async () => {
    if (cart.length === 0) return;
    setSubmitting(true);
    setLastResult(null);
    setError(null);

    const payload = {
      items: cart.map((i) => ({ productId: i.productId, quantity: i.quantity })),
    };

    try {
      const res = await fetch(`${API_BASE}/orders`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const data = await res.json();
      setLastResult(data);

      if (data.status === "CONFIRMED") {
        setCart([]);
      }
      await fetchData();
    } catch (err) {
      setError("Failed to place order. Backend request error.");
    } finally {
      setSubmitting(false);
    }
  };

  // Cancel order
  const handleCancelOrder = async (orderId) => {
    if (!window.confirm("Are you sure you want to cancel this order and restock its items?")) {
      return;
    }
    setCancellingId(orderId);
    setError(null);
    try {
      const res = await fetch(`${API_BASE}/orders/${orderId}/cancel`, {
        method: "POST",
      });
      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.message || `Cancel failed with status ${res.status}`);
      }
      const data = await res.json();
      setLastResult(data);
      await fetchData();
    } catch (err) {
      setError(err.message || "Failed to cancel order.");
    } finally {
      setCancellingId(null);
    }
  };

  return (
    <div className="app-layout">
      <header className="app-header">
        <div className="header-content">
          <h1>Shop Modular Monolith</h1>
          <span className="badge-tech">Java 21 · Spring Boot 3.3 · PostgreSQL</span>
        </div>
        <button className="btn-secondary btn-sm" onClick={fetchData}>
          🔄 Refresh
        </button>
      </header>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="main-grid">
        {/* Left Column: Order Cart Form */}
        <section className="card">
          <h2>🛒 Cart</h2>
          <p className="section-desc">
            Add multiple products to your cart and submit one atomic order with all-or-nothing rollback.
          </p>

          <form onSubmit={handleAddToCart} className="add-item-form">
            <div className="form-row">
              <div className="form-group flex-2">
                <label htmlFor="product-select">Product</label>
                <select
                  id="product-select"
                  value={selectedProductId}
                  onChange={(e) => setSelectedProductId(e.target.value)}
                  disabled={products.length === 0}
                >
                  {products.map((p) => (
                    <option key={p.productId} value={p.productId}>
                      {p.productId} — {p.name} (Stock: {p.stock})
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-group flex-1">
                <label htmlFor="qty-input">Qty</label>
                <input
                  id="qty-input"
                  type="number"
                  min="1"
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                  required
                />
              </div>

              <button
                type="submit"
                className="btn-primary align-self-end"
                disabled={products.length === 0}
              >
                + Add
              </button>
            </div>
          </form>

          {/* Cart Table */}
          <div className="cart-container">
            <h3>Items in Cart ({cart.length})</h3>
            {cart.length === 0 ? (
              <p className="empty-state">Your cart is empty. Select products above to add.</p>
            ) : (
              <>
                <table className="cart-table">
                  <thead>
                    <tr>
                      <th>Product</th>
                      <th>Quantity</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {cart.map((item) => (
                      <tr key={item.productId}>
                        <td>
                          <strong>{item.productId}</strong> — {item.name}
                        </td>
                        <td>
                          <div className="qty-controls">
                            <button
                              type="button"
                              className="btn-qty"
                              onClick={() => handleUpdateCartQty(item.productId, item.quantity - 1)}
                            >
                              -
                            </button>
                            <span>{item.quantity}</span>
                            <button
                              type="button"
                              className="btn-qty"
                              onClick={() => handleUpdateCartQty(item.productId, item.quantity + 1)}
                            >
                              +
                            </button>
                          </div>
                        </td>
                        <td>
                          <button
                            type="button"
                            className="btn-link text-danger"
                            onClick={() => handleRemoveFromCart(item.productId)}
                          >
                            Remove
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                <div className="cart-actions">
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={handleClearCart}
                    disabled={submitting}
                  >
                    Clear Cart
                  </button>
                  <button
                    type="button"
                    className="btn-success"
                    onClick={handlePlaceOrder}
                    disabled={submitting || cart.length === 0}
                  >
                    {submitting ? "Placing Order..." : `Submit Order (${cart.reduce((s, i) => s + i.quantity, 0)} items)`}
                  </button>
                </div>
              </>
            )}
          </div>

          {/* Order Outcome Banner */}
          {lastResult && (
            <div
              className={`result-card ${
                lastResult.status === "CONFIRMED"
                  ? "confirmed"
                  : lastResult.status === "CANCELLED"
                  ? "cancelled"
                  : "rejected"
              }`}
            >
              <h3>
                {lastResult.status === "CONFIRMED" && "✅ Order Confirmed"}
                {lastResult.status === "REJECTED" && "❌ Order Rejected (Rollback)"}
                {lastResult.status === "CANCELLED" && "↩️ Order Cancelled & Restocked"}
              </h3>
              {lastResult.orderId && (
                <p className="order-id-label">
                  Order ID: <code>{lastResult.orderId}</code>
                </p>
              )}
              {lastResult.reason && <p className="reason-text">{lastResult.reason}</p>}
              {lastResult.items && lastResult.items.length > 0 && (
                <div className="outcome-items">
                  <h4>Line Items:</h4>
                  <ul>
                    {lastResult.items.map((it, idx) => (
                      <li key={idx}>
                        <code>{it.productId}</code>: <strong>{it.outcome}</strong>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </section>

        {/* Right Column: Live Inventory Dashboard */}
        <section className="card">
          <h2>📦 Live Inventory Dashboard</h2>
          <p className="section-desc">
            Stock updates in real-time. Products with stock &lt; {LOW_STOCK_THRESHOLD} trigger low-stock alerts.
          </p>

          <table className="data-table">
            <thead>
              <tr>
                <th>Product ID</th>
                <th>Product Name</th>
                <th>Current Stock</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {products.map((p) => {
                const isOut = p.stock === 0;
                const isLow = p.stock > 0 && p.stock < LOW_STOCK_THRESHOLD;
                return (
                  <tr
                    key={p.productId}
                    className={isOut ? "row-out-of-stock" : isLow ? "row-low-stock" : ""}
                  >
                    <td>
                      <code>{p.productId}</code>
                    </td>
                    <td>{p.name}</td>
                    <td>
                      <strong className="stock-count">{p.stock}</strong>
                    </td>
                    <td>
                      {isOut && <span className="status-pill pill-danger">Out of Stock</span>}
                      {isLow && <span className="status-pill pill-warning">Low Stock (&lt; 5)</span>}
                      {!isOut && !isLow && (
                        <span className="status-pill pill-success">In Stock</span>
                      )}
                    </td>
                  </tr>
                );
              })}
              {products.length === 0 && (
                <tr>
                  <td colSpan={4} className="text-center">
                    No products found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </section>
      </div>

      {/* Bottom Grid: Order History & Notification Activity Feed */}
      <div className="bottom-grid">
        {/* Order History */}
        <section className="card">
          <h2>📋 Order History</h2>


          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Order ID</th>
                  <th>Created At</th>
                  <th>Status</th>
                  <th>Line Items</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {orders.map((o) => {
                  const isConfirmed = o.status === "CONFIRMED";
                  const isCancelled = o.status === "CANCELLED";
                  const isRejected = o.status === "REJECTED";

                  return (
                    <tr key={o.orderId}>
                      <td>
                        <code title={o.orderId}>{o.orderId.substring(0, 8)}...</code>
                      </td>
                      <td className="text-muted">
                        {new Date(o.createdAt).toLocaleTimeString([], {
                          hour: "2-digit",
                          minute: "2-digit",
                          second: "2-digit",
                        })}
                      </td>
                      <td>
                        <span
                          className={`status-pill ${
                            isConfirmed
                              ? "pill-success"
                              : isCancelled
                              ? "pill-neutral"
                              : "pill-danger"
                          }`}
                        >
                          {o.status}
                        </span>
                      </td>
                      <td>
                        <div className="item-pills">
                          {o.items?.map((it) => (
                            <span key={it.itemId || it.productId} className="item-pill">
                              {it.productId} ×{it.quantity}
                            </span>
                          ))}
                          {(!o.items || o.items.length === 0) && (
                            <span className="text-muted">None</span>
                          )}
                        </div>
                      </td>
                      <td>
                        {isConfirmed && (
                          <button
                            type="button"
                            className="btn-danger btn-sm"
                            disabled={cancellingId === o.orderId}
                            onClick={() => handleCancelOrder(o.orderId)}
                          >
                            {cancellingId === o.orderId ? "Cancelling..." : "Cancel"}
                          </button>
                        )}
                        {isCancelled && <span className="text-muted">Restocked</span>}
                        {isRejected && <span className="text-muted">No stock held</span>}
                      </td>
                    </tr>
                  );
                })}
                {orders.length === 0 && (
                  <tr>
                    <td colSpan={5} className="text-center">
                      No orders placed yet.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>

        {/* In-Monolith Notification Module Activity Feed */}
        <section className="card">
          <h2>🔔 In-Monolith Notification Feed</h2>
          <p className="section-desc">

          </p>

          <div className="notif-feed">
            {notifications.map((n) => {
              const isConfirmed = n.message.includes("confirmed");
              const isRejected = n.message.includes("rejected");
              const isLowStock = n.message.includes("Low stock");

              return (
                <div
                  key={n.notificationId}
                  className={`notif-card ${
                    isConfirmed ? "notif-confirmed" : isRejected ? "notif-rejected" : "notif-low-stock"
                  }`}
                >
                  <div className="notif-header">
                    <span className="notif-type">
                      {isConfirmed && "✅ Order Placed"}
                      {isRejected && "❌ Order Rejected"}
                      {isLowStock && "⚠️ Reorder Needed"}
                    </span>
                    <span className="notif-time">
                      {new Date(n.createdAt).toLocaleTimeString([], {
                        hour: "2-digit",
                        minute: "2-digit",
                        second: "2-digit",
                      })}
                    </span>
                  </div>
                  <p className="notif-message">{n.message}</p>
                </div>
              );
            })}
            {notifications.length === 0 && (
              <p className="empty-state">No notifications recorded yet. Place an order to see domain events!</p>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}