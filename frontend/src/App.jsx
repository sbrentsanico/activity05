import { useEffect, useState } from "react";

const API_BASE = "http://localhost:8080/api";

export default function App() {
  const [products, setProducts] = useState([]);
  const [productId, setProductId] = useState("");
  const [quantity, setQuantity] = useState(1);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetch(`${API_BASE}/inventory`)
      .then((res) => res.json())
      .then((data) => {
        setProducts(data);
        if (data.length > 0) setProductId(data[0].productId);
      })
      .catch(() => setError("Could not load products. Is the backend running?"));
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setResult(null);
    setError(null);
    try {
      const res = await fetch(`${API_BASE}/orders`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ productId, quantity: Number(quantity) }),
      });
      const data = await res.json();
      setResult(data);
    } catch (err) {
      setError("Request failed. Check that the backend is running on port 8080.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container">
      <h1>Shop - Order Form</h1>
      <form onSubmit={handleSubmit} className="order-form">
        <div className="field">
          <label htmlFor="product">Product</label>
          <select
            id="product"
            value={productId}
            onChange={(e) => setProductId(e.target.value)}
            required
          >
            {products.map((p) => (
              <option key={p.productId} value={p.productId}>
                {p.productId} - {p.name} (stock: {p.stock})
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="quantity">Quantity</label>
          <input
            id="quantity"
            type="number"
            min="1"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            required
          />
        </div>
        <button type="submit" disabled={loading || products.length === 0}>
          {loading ? "Placing order..." : "Place Order"}
        </button>
      </form>

      {error && <div className="result error">{error}</div>}

      {result && (
        <div className={`result ${result.status === "CONFIRMED" ? "confirmed" : "rejected"}`}>
          <h2>
            {result.status === "CONFIRMED" ? "Order CONFIRMED" : "Order REJECTED"}
          </h2>
          {result.reason && <p className="reason">{result.reason}</p>}
          {result.inventory && (
            <div className="inventory-info">
              <h3>Current Inventory</h3>
              <table>
                <tbody>
                  <tr><td>Product ID</td><td>{result.inventory.productId}</td></tr>
                  <tr><td>Name</td><td>{result.inventory.name}</td></tr>
                  <tr><td>Stock remaining</td><td>{result.inventory.stock}</td></tr>
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}