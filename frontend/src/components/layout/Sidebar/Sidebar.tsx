import { NavLink } from "react-router-dom";
import { useState } from "react";
import "./Sidebar.css";
import {
  LayoutDashboard,
  Utensils,
  Dumbbell,
  ChartNoAxesCombined,
  Settings,
  Sprout,
  Menu,
  X,
  Leaf,
  CircleHelp,
} from "lucide-react";

const navigation = [
  { name: "Daily dashboard", path: "/", icon: LayoutDashboard },
  { name: "Nutrition", path: "/nutrition", icon: Utensils },
  { name: "Workouts", path: "/workouts", icon: Dumbbell },
  { name: "History", path: "/history", icon: ChartNoAxesCombined },
  { name: "Settings", path: "/settings", icon: Settings },
];

function Sidebar() {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  return (
    <aside className="sidebar">
      <div className="sidebar-top">
        <div className="sidebar-logo">
          <Sprout size={26} />
          <span>veya</span>
        </div>
        <button
          className="menu-toggle"
          onClick={() => setIsMenuOpen(!isMenuOpen)}
          aria-label={isMenuOpen ? "Close menu" : "Open menu"}
          aria-expanded={isMenuOpen}
        >
          {isMenuOpen ? <X /> : <Menu />}
        </button>
      </div>
      <nav className={`sidebar-navigation ${isMenuOpen ? "is-open" : ""}`}>
        {navigation.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.path}
              to={item.path}
              onClick={() => setIsMenuOpen(false)}
              end={item.path === "/"}
              className={({ isActive }) =>
                `nav-link ${isActive ? "active" : ""}`
              }
            >
              <Icon />
              <span>{item.name}</span>
            </NavLink>
          );
        })}
      </nav>
      <div className="sidebar-bottom">
        <div className="pace-card">
          <Leaf size={16} className="pace-card-icon" />
          <h4>At your own pace</h4>
          <p>Plans can change. No need to start over.</p>
        </div>
        <button className="help-link">
          <CircleHelp size={16} />
          <span>Help and support</span>
        </button>
        <button className="sidebar-profile">
          <span className="avatar">AK</span>
          <span className="profile-info">
            <strong>Anna K.</strong>
            <span>My account</span>
          </span>
        </button>
      </div>
    </aside>
  );
}

export default Sidebar;
