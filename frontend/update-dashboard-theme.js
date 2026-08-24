const fs = require('fs');
const path = require('path');

const cssFiles = [
  path.join(__dirname, 'src', 'app', 'pages', 'dashboard', 'admin', 'admin.component.css'),
  path.join(__dirname, 'src', 'app', 'pages', 'dashboard', 'vendedor', 'vendedor.component.css')
];

for (const file of cssFiles) {
  if (!fs.existsSync(file)) continue;
  
  let css = fs.readFileSync(file, 'utf8');

  // 1. General Dashboard (Light bg, Dark text)
  css = css.replace(/background-color:\s*#0f111a;/g, 'background-color: #f4f6f8;');
  css = css.replace(/color:\s*#f7fafc;/g, 'color: #1e293b;');

  // 2. Sidebar (FarmaCode Dark Blue)
  css = css.replace(/background-color:\s*#161925;/g, 'background-color: #111d2e;');
  css = css.replace(/border-right:\s*1px solid rgba\(255, 255, 255, 0\.05\);/g, 'border-right: 1px solid rgba(255, 255, 255, 0.1);');
  
  // 3. Brand/Primary Gradients (Cyan -> FarmaCode Orange)
  css = css.replace(/linear-gradient\(135deg,\s*#00f2fe 0%,\s*#4facfe 100%\)/g, 'linear-gradient(135deg, #ea580c 0%, #c2410c 100%)');
  css = css.replace(/rgba\(79, 172, 254,\s*0\.3\)/g, 'rgba(234, 88, 12, 0.3)');
  css = css.replace(/#00f2fe/g, '#ea580c');
  
  // 4. Active Nav Item
  css = css.replace(/linear-gradient\(135deg,\s*rgba\(0, 242, 254,\s*0\.15\)\s*0%,\s*rgba\(79, 172, 254,\s*0\.1\)\s*100%\)/g, 'rgba(234, 88, 12, 0.15)');
  
  // 5. User Profile
  // Let's leave avatar orange, but role text lighter
  css = css.replace(/\.user-role\s*{[^}]*color:\s*#ea580c;[^}]*}/, '.user-role {\n  font-size: 12px;\n  color: #8fa8c8;\n  font-weight: 700;\n  letter-spacing: 0.5px;\n}');
  
  // 6. Main Content Titles (White -> Dark Blue)
  css = css.replace(/\.header-title\s*h1\s*{[^}]*color:\s*white;/g, '.header-title h1 {\n  font-size: 28px;\n  font-weight: 700;\n  margin: 0;\n  color: #1a2a4a;');
  css = css.replace(/\.date-badge\s*{[^}]*background:\s*rgba\(255,\s*255,\s*255,\s*0\.03\);/g, '.date-badge {\n  background: white;\n  border: 1px solid #e2e8f0;\n  color: #64748b;');
  
  // 7. Cards
  css = css.replace(/background:\s*rgba\(22,\s*25,\s*37,\s*0\.6\);/g, 'background: white;');
  css = css.replace(/background:\s*rgba\(22,\s*25,\s*37,\s*0\.65\);/g, 'background: white;');
  css = css.replace(/border:\s*1px solid rgba\(255,\s*255,\s*255,\s*0\.05\);/g, 'border: 1px solid #e2e8f0;');
  css = css.replace(/box-shadow:\s*0 4px 20px rgba\(0,\s*0,\s*0,\s*0\.1\);/g, 'box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);');
  css = css.replace(/box-shadow:\s*0 10px 30px rgba\(0,\s*0,\s*0,\s*0\.15\);/g, 'box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.05), 0 4px 6px -2px rgba(0, 0, 0, 0.03);');
  
  // 8. Metric Details
  css = css.replace(/\.metric-details\s*h3\s*{[^}]*color:\s*#a0aec0;/g, '.metric-details h3 {\n  font-size: 14px;\n  color: #64748b;');
  css = css.replace(/\.metric-value\s*{[^}]*color:\s*white;/g, '.metric-value {\n  font-size: 24px;\n  font-weight: 700;\n  margin: 0;\n  color: #1e293b;');
  
  // 9. Card Headers
  css = css.replace(/\.card-header\s*h2\s*{[^}]*color:\s*white;/g, '.card-header h2 {\n  font-size: 18px;\n  font-weight: 700;\n  margin: 0;\n  color: #1e293b;');
  
  // 10. Tables
  css = css.replace(/background:\s*rgba\(255,\s*255,\s*255,\s*0\.01\);/g, 'background: #f8fafc;');
  css = css.replace(/border-bottom:\s*1px solid rgba\(255,\s*255,\s*255,\s*0\.03\);/g, 'border-bottom: 1px solid #f1f5f9;');
  css = css.replace(/\.dashboard-table\s*th\s*{[^}]*color:\s*#cbd5e0;/g, '.dashboard-table th {\n  padding: 16px 24px;\n  color: #64748b;');
  css = css.replace(/\.dashboard-table\s*td\s*{[^}]*color:\s*#cbd5e0;/g, '.dashboard-table td {\n  padding: 18px 24px;\n  border-bottom: 1px solid #f1f5f9;\n  font-size: 14px;\n  color: #475569;\n  vertical-align: middle;');
  css = css.replace(/\.bold-text\s*{[^}]*color:\s*white;/g, '.bold-text {\n  font-weight: 600;\n  color: #1e293b;');
  css = css.replace(/\.prod-title\s*{[^}]*color:\s*white;/g, '.prod-title {\n  color: #1e293b;\n  font-weight: 600;');
  css = css.replace(/border:\s*1px solid rgba\(255,\s*255,\s*255,\s*0\.08\);/g, 'border: 1px solid #e2e8f0;');

  // 11. Search Box
  css = css.replace(/background:\s*rgba\(15,\s*17,\s*26,\s*0\.5\);/g, 'background: white;');
  css = css.replace(/background:\s*rgba\(15,\s*17,\s*26,\s*0\.8\);/g, 'background: white;');
  css = css.replace(/\.search-box\s*input\s*{[^}]*color:\s*white;/g, '.search-box input {\n  width: 100%;\n  padding: 10px 14px 10px 38px;\n  background: white;\n  border: 1px solid #e2e8f0;\n  border-radius: 10px;\n  color: #1e293b;');

  // 12. Edit Button
  css = css.replace(/\.btn-action\.edit-btn\s*{[^}]*color:\s*#cbd5e0;/g, '.btn-action.edit-btn {\n  background: white;\n  border: 1px solid #e2e8f0;\n  color: #64748b;');
  css = css.replace(/\.btn-action\.edit-btn:hover\s*{[^}]*color:\s*white;/g, '.btn-action.edit-btn:hover {\n  background: #ea580c;\n  color: white;');

  // 13. Modals
  css = css.replace(/background:\s*#1a1c28;/g, 'background: white;');
  css = css.replace(/background:\s*rgba\(0,0,0,0\.15\);/g, 'background: #f8fafc;');
  css = css.replace(/background:\s*rgba\(255,255,255,0\.02\);/g, 'background: #f1f5f9;');
  css = css.replace(/border-bottom:\s*1px solid rgba\(255,255,255,0\.05\);/g, 'border-bottom: 1px solid #e2e8f0;');
  css = css.replace(/\.modal-header\s*h2\s*{[^}]*color:\s*white;/g, '.modal-header h2 {\n  font-size: 18px;\n  font-weight: 700;\n  margin: 0;\n  color: #1e293b;');
  css = css.replace(/\.modal-total\s*{[^}]*color:\s*white;/g, '.modal-total {\n  border-top: 1px solid #e2e8f0;\n  padding-top: 20px;\n  display: flex;\n  justify-content: space-between;\n  align-items: center;\n  font-weight: 700;\n  font-size: 16px;\n  color: #1e293b;');
  css = css.replace(/\.total-val\s*{[^}]*font-size:\s*20px;/g, '.total-val {\n  color: #ea580c;\n  font-size: 20px;');
  css = css.replace(/\.info-block\s*p\s*{[^}]*color:\s*#cbd5e0;/g, '.info-block p {\n  margin: 6px 0;\n  color: #475569;');
  css = css.replace(/\.modal-table\s*td\s*{[^}]*color:\s*#cbd5e0;/g, '.modal-table td {\n  padding: 14px 16px;\n  border-bottom: 1px solid #f1f5f9;\n  font-size: 13px;\n  color: #475569;');
  css = css.replace(/\.prod-preview\s*h4\s*{[^}]*color:\s*white;/g, '.prod-preview h4 {\n  margin: 0;\n  color: #1e293b;');

  // Modal Counter
  css = css.replace(/background:\s*rgba\(0,0,0,0\.2\);/g, 'background: white;');
  css = css.replace(/\.btn-counter\s*{[^}]*color:\s*white;/g, '.btn-counter {\n  width: 50px;\n  height: 50px;\n  background: #f1f5f9;\n  border: none;\n  color: #1e293b;');
  css = css.replace(/\.input-counter-val\s*{[^}]*color:\s*white;/g, '.input-counter-val {\n  width: 80px;\n  height: 50px;\n  background: transparent;\n  border: none;\n  border-left: 1px solid #e2e8f0;\n  border-right: 1px solid #e2e8f0;\n  color: #1e293b;');

  fs.writeFileSync(file, css);
}
console.log('Successfully updated themes for Admin and Vendedor dashboards.');
