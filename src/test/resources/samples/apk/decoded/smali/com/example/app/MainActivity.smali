.class public Lcom/example/app/MainActivity;
.super Landroidx/appcompat/app/AppCompatActivity;
.implements Landroid/view/View$OnClickListener;
.source "MainActivity.java"

# instance fields
.field private presenter:Lcom/example/app/MainPresenter;

# direct methods
.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Landroidx/appcompat/app/AppCompatActivity;-><init>()V
    return-void
.end method

.method public onClick(Landroid/view/View;)V
    .registers 2
    iget-object v0, p0, Lcom/example/app/MainActivity;->presenter:Lcom/example/app/MainPresenter;
    invoke-virtual {v0}, Lcom/example/app/MainPresenter;->load()V
    return-void
.end method
