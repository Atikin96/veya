import { Bell, ChevronDown,LogOut  } from "lucide-react";
import { Link, useLocation } from "react-router-dom";
import "./Header.css";
const navigation = [
  { name: "Daily dashboard", path: "/" },
  { name: "Nutrition", path: "/nutrition" },
  { name: "Replace dinner", path: "/nutrition/replace-dinner" },
  { name: "Plan review", path: "/nutrition/plan-review" },
  { name: "Workouts", path: "/workouts" },
  { name: "History", path: "/history" },
  { name: "Settings", path: "/settings" },
];
function Header() {
  const location = useLocation();
  const currentPage = navigation.find(
    (item) => item.path === location.pathname,
  );
  const parentPath = "/" + location.pathname.split("/")[1];
  const parentPage =
    parentPath !== location.pathname
      ? navigation.find((item) => item.path === parentPath)
      : null;
    const handleSignOut = () => {
    localStorage.removeItem("registered");
    window.location.href = "/";
  };
  return (
    <header className="header">
      <div className="header-breadcrumb">
        <Link to="/">My space /</Link>
                {parentPage && (
          <>
            <Link to={parentPage.path}>{parentPage.name}</Link>
            <span className="breadcrumb-divider">/</span>
          </>
        )}
        <span className="current-page">{currentPage?.name}</span>
      </div>
      <div className="btns-notification-language">
        <button className="notification-button" aria-label="Notifications">
          <Bell size={20} />
        </button>
        <button className="language-button" aria-label="Select language">
          <span>EN</span>
          <ChevronDown size={15} />
        </button>
        <button className="signout-button" onClick={handleSignOut}>
          <LogOut size={15} />
          <span>Sign out</span>
        </button>
      </div>
    </header>
  );
}

export default Header;
