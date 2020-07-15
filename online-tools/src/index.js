//import './mini-dark.css';

var clearInputs = [];
var setInputs = [];
const hex = ['0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F','Wrong'];

function updateResult() {
    var highResult = "fn:translate($input,'";
    var highAppend = "";
    var mask = 0;
    var umask = 15;
    for(var i = 0;i<4;i++) {
        if(setInputs[i].checked) {
            var v = parseInt(setInputs[i].value);
            mask |= v;
            console.debug("Set["+v+"]", mask);
        }
        else if(clearInputs[i].checked) {
            var v = parseInt(clearInputs[i].value);
            umask &= ~v;
            console.debug("Clear["+v+"]", umask);
        }
    }
    for(var j = 0;j<16;j++) {
        highResult += hex[j];
        highAppend += hex[((j | mask) & umask) & 15];
    }
    console.debug("highResult",highResult,highAppend);
    document.mainForm.highResult.value = highResult + "','"+highAppend+"')";
    document.mainForm.assigning.value = hex[mask];
}

function handleClear(event) {
    console.debug("handleClear",event);
    var t = event.target;
    var n = t.name;
    n = n.replace('clear','set');
    setInputs.forEach(e => {
        if(e.name == n)
            e.disabled = t.checked;
    })
    updateResult();
}

function handleSet(event) {
    console.debug("handleSet",event);
    var t = event.target;
    var n = t.name;
    n = n.replace('set','clear');
    clearInputs.forEach(e => {
        if(e.name == n)
            e.disabled = t.checked;
    })
    updateResult();
}

function onLoad() {
    console.debug("Form: ",document.forms[0], document.mainForm.elements);
    
    for(var i=0;i<document.mainForm.elements.length;i++) {
        var e = document.mainForm.elements[i];
        //console.log(e);
        if(e.type == 'checkbox') {
            if(e.name.match(/^clear\d+/)) {
                clearInputs.push(e);
                e.addEventListener('change',handleClear);
            }
            if(e.name.match(/^set\d+/)) {
                setInputs.push(e);       
                e.addEventListener('change',handleSet);
            }
        }
        else if(e.type == 'text')
        e.addEventListener('focus',(e) => {
            //console.debug(e);
            window.setTimeout(
                () => e.target.select(),100
            );
        });
    }
    updateResult();
  }
  
  window.onload = onLoad;