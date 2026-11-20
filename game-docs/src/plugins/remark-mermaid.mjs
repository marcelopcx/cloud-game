// Los bloques ```mermaid pasan a <pre class="mermaid"> antes de que Expressive Code los tome
// como código; components/Head.astro los dibuja en el navegador.
const escape = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

export default function remarkMermaid() {
  const visit = (node) => {
    node.children?.forEach((child, i) => {
      if (child.type === 'code' && child.lang === 'mermaid') {
        node.children[i] = { type: 'html', value: `<pre class="mermaid">${escape(child.value)}</pre>` };
      } else {
        visit(child);
      }
    });
  };
  return visit;
}
