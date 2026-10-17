const http=require('http'),fs=require('fs'),path=require('path');
const root=path.join(__dirname,'app/src/main/assets');
http.createServer((req,res)=>{
  let p=decodeURIComponent(req.url.split('?')[0]); if(p==='/')p='/index.html';
  const f=path.join(root,p);
  fs.readFile(f,(e,d)=>{
    if(e){res.writeHead(404);res.end('404');return;}
    const ext=path.extname(f);
    const t={'.html':'text/html','.css':'text/css','.js':'text/javascript','.png':'image/png','.svg':'image/svg+xml'}[ext]||'application/octet-stream';
    res.writeHead(200,{'Content-Type':t});res.end(d);
  });
}).listen(8484,'127.0.0.1',()=>console.log('serving '+root));
