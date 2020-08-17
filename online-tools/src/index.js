//import './mini-dark.css';

var clearInputs = [];
var setInputs = [];
const hex = ['0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F','a','b','c','d','e','f','Wrong'];

function updateResult() {
    var b = document.mainForm.varname.value;
    if(!b)
      b = '$input';

    var toNb = "1";
    var mask = 0;
    var umask = 15;

    for(var i = 0;i<4;i++) {
      var sb = b;
      if(i>0)
        sb += ""+i;
      if(setInputs[i].checked) {
          var v = parseInt(setInputs[i].value);
          mask |= v;
          console.debug("Set["+v+"]", mask);
          toNb += '+'+(1 << (3-i));
      }
      else if(clearInputs[i].checked) {
          var v = parseInt(clearInputs[i].value);
          umask &= ~v;
          console.debug("Clear["+v+"]", umask);
          //toNb += 0 * (1 << (3-i));
      }
      else {
        toNb +='+number('+sb+')'+'*'+(1 << (3-i));
      }
    }

    var sameUnpack = "fn:translate("+b+",'";
    var toUncomp = "fn:translate("+b+",'";
    var toUnpack = "fn:substring('0123456789ABCDEF',";
    var toUnpackPost = ",1)";
    var fromChar = "";
    var toChar = "";
    var uncomp1 = '';
    var uncomp2 = '';
    var uncomp3 = '';
    var uncomp4 = '';
    for(var j = 0;j<16+6;j++) {
      var r = (((j >= 16 ? j-6 : j) | mask) & umask) & 15;
      fromChar += hex[j];
      toChar += hex[r];
      uncomp1 += r & 8 ? '1':'0';
      uncomp2 += r & 4 ? '1':'0';
      uncomp3 += r & 2 ? '1':'0';
      uncomp4 += r & 1 ? '1':'0';
    }

    console.debug("mapping",fromChar,toChar);
    document.mainForm.highResult.value = sameUnpack + fromChar + "','"+toChar+"')";
    document.mainForm.assigning.value = hex[mask];

    document.mainForm.toUncomp1.value = toUncomp +fromChar+ "','"+uncomp1+"')";
    document.mainForm.toUncomp2.value = toUncomp +fromChar+ "','"+uncomp2+"')";
    document.mainForm.toUncomp3.value = toUncomp +fromChar+ "','"+uncomp3+"')";
    document.mainForm.toUncomp4.value = toUncomp +fromChar+ "','"+uncomp4+"')";
    document.mainForm.toUnpack.value = toUnpack + toNb + toUnpackPost;
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
    document.mainForm.varname.addEventListener('change',updateResult);
    updateResult();
  }
  
  window.onload = onLoad;
