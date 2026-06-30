.class public Lcom/example/app/MainPresenter;
.super Ljava/lang/Object;
.source "MainPresenter.java"

.field private final repo:Lcom/example/app/util/Repository;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public load()V
    .registers 2
    iget-object v0, p0, Lcom/example/app/MainPresenter;->repo:Lcom/example/app/util/Repository;
    invoke-interface {v0}, Lcom/example/app/util/Repository;->fetch()Ljava/lang/String;
    move-result-object v0
    invoke-static {v0}, Landroid/util/Log;->d(Ljava/lang/String;)I
    return-void
.end method
