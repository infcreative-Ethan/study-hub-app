import sys,subprocess,numpy as np,json,os
def load(f):
    raw=subprocess.run(["ffmpeg","-v","quiet","-i",f,"-ac","1","-ar","16000","-f","s16le","-"],capture_output=True).stdout
    return np.frombuffer(raw,np.int16).astype(np.float32)/32768
def segs(f,gap=0.35,minlen=0.25,rel=0.06):
    x=load(f);hop=320
    n=len(x)//hop; e=np.sqrt((x[:n*hop].reshape(n,hop)**2).mean(1))
    db=20*np.log10(e+1e-9); thr=max(np.percentile(db,95)-28, -50)
    on=db>thr; out=[];i=0
    while i<n:
        if on[i]:
            j=i
            while j<n and on[j]: j+=1
            out.append([i*0.02,j*0.02]); i=j
        else: i+=1
    m=[]
    for s in out:
        if m and s[0]-m[-1][1]<gap: m[-1][1]=s[1]
        else: m.append(s)
    return [[round(a,2),round(b,2)] for a,b in m if b-a>=minlen]
if __name__=="__main__":
    gap=float(sys.argv[2]) if len(sys.argv)>2 else 0.35
    s=segs(sys.argv[1],gap); print(len(s)); print(s)
